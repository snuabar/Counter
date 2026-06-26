package com.snuabar.counter.core.detection.tflite

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.snuabar.counter.core.detection.CountEvent
import com.snuabar.counter.core.detection.DetectionConfig
import com.snuabar.counter.core.detection.DetectionEngine
import com.snuabar.counter.core.detection.tflite.action.ActionDetectorFactory
import com.snuabar.counter.core.detection.tflite.action.PoseActionDetector
import com.snuabar.counter.core.template.RecordingSession
import com.snuabar.counter.domain.model.ActionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * TFLite Detection Engine using pose detection model.
 *
 * Supports MoveNet/SinglePose or similar models that output keypoint coordinates.
 * Model file should be placed in app/src/main/assets/ as "pose_detection.tflite"
 *
 * Input:  192x192 or 256x256 RGB image
 * Output: Keypoint coordinates (x, y, confidence) for body joints
 */
class TFLiteDetectionEngine @Inject constructor(
    private val context: Context,
    private val recordingSession: RecordingSession
) : DetectionEngine {

    private val _countEvents = MutableStateFlow(CountEvent(count = 0, confidence = 0f))
    override val countEvents: Flow<CountEvent> = _countEvents.asStateFlow()

    // Keypoints for visualization: array of [y, x, confidence] for each keypoint
    private val _keypoints = MutableStateFlow<Array<FloatArray>?>(null)
    val keypoints: Flow<Array<FloatArray>?> = _keypoints.asStateFlow()

    private var interpreter: Interpreter? = null
    private var threshold = 0.7f
    private var isRunning = false
    private var isPaused = false
    private var count = 0

    override fun isRunning(): Boolean = isRunning

    // Throttle frame processing to max ~10 fps (same as counting page)
    private var lastAnalyzeTime = 0L

    // Model configuration
    private var modelInputSize = 192
    private val numKeypoints = 17     // COCO format: nose, eyes, ears, shoulders, elbows, wrists, hips, knees, ankles
    private var isFloatInput = false  // auto-detected from model tensor type
    private var outputShape4D = false // true if output is [1,1,17,3], false if [1,17,3]

    // Action detection
    private var actionDetector: PoseActionDetector? = null
    private var currentActionType: ActionType = ActionType.CUSTOM

    // Async analysis executor to avoid blocking CameraX thread
    // Queue size=1: discard old tasks, always process the latest frame
    private val analysisExecutor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(1),
        ThreadPoolExecutor.DiscardPolicy()
    )
    @Volatile
    private var isAnalyzing = false

    companion object {
        private const val DEFAULT_MODEL_PATH = "pose_detection.tflite"
    }

    override fun start(config: DetectionConfig) {
        if (isRunning) {
            // Already running (e.g. from preview mode) - just reset action detector for counting
            threshold = config.threshold
            count = 0
            _countEvents.value = CountEvent(count = 0, confidence = 0f)
            currentActionType = config.actionType
            actionDetector = ActionDetectorFactory.createDetector(
                template = config.template
            )
            actionDetector?.reset()
            return
        }
        isRunning = true
        isPaused = false
        threshold = config.threshold
        count = 0
        _countEvents.value = CountEvent(count = 0, confidence = 0f)

        // Load model based on configuration
        modelInputSize = config.poseModelConfig.inputSize
        initInterpreter(config.poseModelConfig.fileName)
        
        // Initialize action detector based on config
        currentActionType = config.actionType
        actionDetector = ActionDetectorFactory.createDetector(
            template = config.template
        )
        actionDetector?.reset()
    }

    /**
     * Start inference only for skeleton display, without action detection/counting.
     */
    fun startPreview(config: DetectionConfig) {
        if (isRunning) return
        isRunning = true
        isPaused = false
        modelInputSize = config.poseModelConfig.inputSize
        initInterpreter(config.poseModelConfig.fileName)
        // No action detector - just inference for skeleton
    }

    private fun initInterpreter(modelPath: String = DEFAULT_MODEL_PATH) {
        try {
            val assetManager = context.assets
            val fileDescriptor = assetManager.openFd(modelPath)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer: MappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY, startOffset, declaredLength
            )

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            // Auto-detect input data type from model tensor
            val inputTensor = interpreter!!.getInputTensor(0)
            val inputType = inputTensor.dataType()
            isFloatInput = (inputType == org.tensorflow.lite.DataType.FLOAT32)
            val inputShape = inputTensor.shape().toList()
            // Auto-detect output shape
            val outTensor = interpreter!!.getOutputTensor(0)
            val outShape = outTensor.shape().toList()
            outputShape4D = (outShape.size == 4)
            android.util.Log.d("TFLite", "Model loaded: $modelPath input=$inputType $inputShape output=$outShape")
        } catch (e: Exception) {
            android.util.Log.e("TFLite", "Model load failed: ${e.message}")
        }
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        isPaused = false
    }

    override fun stop() {
        isRunning = false
        isPaused = false
        count = 0
        synchronized(this) {
            interpreter?.close()
            interpreter = null
        }
        actionDetector?.reset()
        actionDetector = null
        _keypoints.value = null
        analysisExecutor.shutdownNow()
    }

    /**
     * Stop counting but keep inference running for skeleton display.
     */
    fun stopCounting() {
        actionDetector?.reset()
        actionDetector = null
        count = 0
        _countEvents.value = CountEvent(count = 0, confidence = 0f)
    }

    override fun setThreshold(threshold: Float) {
        this.threshold = threshold
    }

    /**
     * Process an ImageProxy directly (for CameraX compatibility, now used internally).
     * @return true if the frame was processed, false if skipped
     */
    fun processImageProxy(imageProxy: ImageProxy): Boolean {
        if (!isRunning || isPaused) {
            imageProxy.close()
            return false
        }
        // Fixed interval: 10 fps (same as counting page)
        val now = System.currentTimeMillis()
        if (now - lastAnalyzeTime < 100) {
            imageProxy.close()
            return false
        }
        lastAnalyzeTime = now
        // Skip if previous frame still being processed
        if (isAnalyzing) {
            imageProxy.close()
            return false
        }
        isAnalyzing = true
        try {
            // Skip if resolution is too high (fallback in case ResolutionSelector fails)
            if (imageProxy.width > 1280 || imageProxy.height > 720) {
                android.util.Log.w("TFLite", "Skipping high resolution frame: ${imageProxy.width}x${imageProxy.height}")
                imageProxy.close()
                isAnalyzing = false
                return false
            }
            val startTime = System.currentTimeMillis()
            val bitmap = imageProxyToBitmap(imageProxy)
            val convertTime = System.currentTimeMillis() - startTime
            android.util.Log.d("TFLite", "toBitmap: ${imageProxy.width}x${imageProxy.height} in ${convertTime}ms")
            imageProxy.close()
            isAnalyzing = false // Unlock immediately after conversion
            if (bitmap != null) {
                analysisExecutor.execute {
                    try {
                        synchronized(this) {
                            if (isRunning && !isPaused) {
                                runInference(bitmap)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TFLite", "analyze inference error: ${e.message}")
                    } finally {
                        bitmap.recycle()
                    }
                }
                return true
            }
            return false
        } catch (e: Exception) {
            android.util.Log.e("TFLite", "analyze error: ${e.message}")
            imageProxy.close()
            isAnalyzing = false
            return false
        }
    }

    /**
     * Process a bitmap directly (for Camera2 or other sources).
     */
    fun processBitmap(bitmap: Bitmap) {
        if (!isRunning || isPaused) return
        try {
            synchronized(this) {
                if (interpreter != null) {
                    runInference(bitmap)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TFLite", "processBitmap error: ${e.message}")
        }
    }

    private fun runInference(bitmap: Bitmap) {
        val inputBuffer = bitmapToInputBuffer(bitmap)

        // Output buffer: auto-detect shape [1,1,17,3] or [1,17,3]
        val keypoints: Array<FloatArray>
        if (outputShape4D) {
            val outputBuffer = Array(1) { Array(1) { Array(numKeypoints) { FloatArray(3) } } }
            interpreter?.run(inputBuffer, outputBuffer)
            keypoints = outputBuffer[0][0]
        } else {
            val outputBuffer = Array(1) { Array(numKeypoints) { FloatArray(3) } }
            interpreter?.run(inputBuffer, outputBuffer)
            keypoints = outputBuffer[0]
        }

        // Store keypoints for visualization
        _keypoints.value = keypoints

        // Forward keypoints to recording session if active
        if (recordingSession.isRecording()) {
            recordingSession.onKeypointsDetected(keypoints)
        }

        val result = actionDetector?.detect(keypoints)
        
        // Forward debug info even when no count detected
        if (result != null) {
            if (result.isDetected) {
                count = result.count
            }
            _countEvents.value = CountEvent(
                count = count,
                confidence = result.confidence,
                debugInfo = result.structuredDebugInfo
            )
        }
    }

    /**
     * Convert ImageProxy (YUV_420_888) to Bitmap (for CameraX template recording).
     * Uses direct YUV->RGB conversion (same logic as CountingScreen.yuvToBitmap).
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val width = imageProxy.width
            val height = imageProxy.height
            val yPlane = imageProxy.planes[0]
            val uPlane = imageProxy.planes[1]
            val vPlane = imageProxy.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            val bitmap = createBitmap(width, height)
            val pixels = IntArray(width * height)

            for (row in 0 until height) {
                val yRowOffset = row * yRowStride
                val uvRowOffset = (row / 2) * uvRowStride
                for (col in 0 until width) {
                    val yIdx = yRowOffset + col * yPlane.pixelStride
                    val uvIdx = uvRowOffset + (col / 2) * uvPixelStride

                    val y = (yBuffer.get(yIdx).toInt() and 0xFF) - 16
                    val u = (uBuffer.get(uvIdx).toInt() and 0xFF) - 128
                    val v = (vBuffer.get(uvIdx).toInt() and 0xFF) - 128

                    val yy = y.coerceAtLeast(0)
                    var r = (1.164f * yy + 1.596f * v).toInt()
                    var g = (1.164f * yy - 0.813f * v - 0.391f * u).toInt()
                    var b = (1.164f * yy + 2.018f * u).toInt()

                    r = r.coerceIn(0, 255)
                    g = g.coerceIn(0, 255)
                    b = b.coerceIn(0, 255)

                    pixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("TFLite", "imageProxyToBitmap error: ${e.message}")
            null
        }
    }

    /**
     * Convert Bitmap to TFLite input buffer (auto-detects uint8 or float32 from model)
     */
    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = bitmap.scale(modelInputSize, modelInputSize)
        val intValues = IntArray(modelInputSize * modelInputSize)
        resizedBitmap.getPixels(intValues, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)

        val inputBuffer = if (isFloatInput) {
            // Float32 model: 4 bytes per channel, normalized to [0, 1]
            val buf = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3)
            buf.order(ByteOrder.nativeOrder())
            for (pixelValue in intValues) {
                buf.putFloat((pixelValue shr 16 and 0xFF) / 255.0f)
                buf.putFloat((pixelValue shr 8 and 0xFF) / 255.0f)
                buf.putFloat((pixelValue and 0xFF) / 255.0f)
            }
            buf
        } else {
            // Uint8 model: 1 byte per channel
            val buf = ByteBuffer.allocateDirect(modelInputSize * modelInputSize * 3)
            buf.order(ByteOrder.nativeOrder())
            for (pixelValue in intValues) {
                buf.put((pixelValue shr 16 and 0xFF).toByte())
                buf.put((pixelValue shr 8 and 0xFF).toByte())
                buf.put((pixelValue and 0xFF).toByte())
            }
            buf
        }

        inputBuffer.rewind()
        if (resizedBitmap != bitmap) resizedBitmap.recycle()
        return inputBuffer
    }

    /**
     * Set the action type for detection (e.g., PUSH_UP, SQUAT).
     * Must be called before start().
     */
    fun setActionType(actionType: ActionType) {
        currentActionType = actionType
    }
}
