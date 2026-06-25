package com.snuabar.counter.core.detection.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.snuabar.counter.core.detection.*
import com.snuabar.counter.core.detection.tflite.action.ActionDetectorFactory
import com.snuabar.counter.domain.model.ActionType
import com.snuabar.counter.core.detection.tflite.action.PoseActionDetector
import com.snuabar.counter.core.template.RecordingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
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
) : DetectionEngine, ImageAnalysis.Analyzer {

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

    // Model configuration
    private var modelInputSize = 192
    private val numKeypoints = 17     // COCO format: nose, eyes, ears, shoulders, elbows, wrists, hips, knees, ankles
    private var isFloatInput = false  // auto-detected from model tensor type
    private var outputShape4D = false // true if output is [1,1,17,3], false if [1,17,3]

    // Action detection
    private var actionDetector: PoseActionDetector? = null
    private var currentActionType: ActionType = ActionType.PUSH_UP

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
                currentActionType,
                template = if (currentActionType == ActionType.CUSTOM) config.template else null
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
            currentActionType,
            template = if (currentActionType == ActionType.CUSTOM) config.template else null
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
     * ImageAnalysis.Analyzer implementation for CameraX integration (used by template recording).
     */
    override fun analyze(imageProxy: ImageProxy) {
        if (!isRunning || isPaused) {
            imageProxy.close()
            return
        }
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                runInference(bitmap)
                bitmap.recycle()
            }
        } catch (e: Exception) {
            android.util.Log.e("TFLite", "analyze error: ${e.message}")
        } finally {
            imageProxy.close()
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
        
        if (result != null && result.isDetected) {
            count = result.count
            _countEvents.value = CountEvent(
                count = count,
                confidence = result.confidence
            )
        }
    }

    /**
     * Convert ImageProxy (YUV_420_888) to Bitmap (for CameraX template recording).
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    /**
     * Convert Bitmap to TFLite input buffer (auto-detects uint8 or float32 from model)
     */
    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)
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
