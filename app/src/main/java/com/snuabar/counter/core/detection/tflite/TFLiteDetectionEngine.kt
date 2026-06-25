package com.snuabar.counter.core.detection.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.snuabar.counter.core.detection.*
import com.snuabar.counter.core.detection.tflite.action.ActionDetectorFactory
import com.snuabar.counter.core.detection.tflite.action.ActionType
import com.snuabar.counter.core.detection.tflite.action.PoseActionDetector
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
import kotlin.math.max

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
    private val context: Context
) : DetectionEngine, ImageAnalysis.Analyzer {

    private val _countEvents = MutableStateFlow(CountEvent(count = 0, confidence = 0f))
    override val countEvents: Flow<CountEvent> = _countEvents.asStateFlow()

    private var interpreter: Interpreter? = null
    private var threshold = 0.7f
    private var isRunning = false
    private var isPaused = false
    private var count = 0

    // Model configuration
    private var modelInputSize = 192
    private val numKeypoints = 17     // COCO format: nose, eyes, ears, shoulders, elbows, wrists, hips, knees, ankles

    // Action detection
    private var actionDetector: PoseActionDetector? = null
    private var currentActionType: ActionType = ActionType.PUSH_UP

    companion object {
        private const val DEFAULT_MODEL_PATH = "pose_detection.tflite"
    }

    override fun start(config: DetectionConfig) {
        if (isRunning) return
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
        actionDetector = ActionDetectorFactory.createDetector(currentActionType)
        actionDetector?.reset()
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
        } catch (e: Exception) {
            e.printStackTrace()
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
        interpreter?.close()
        interpreter = null
        actionDetector?.reset()
        actionDetector = null
    }

    override fun setThreshold(threshold: Float) {
        this.threshold = threshold
    }

    /**
     * ImageAnalysis.Analyzer implementation for CameraX integration.
     */
    override fun analyze(imageProxy: ImageProxy) {
        processFrame(imageProxy)
    }

    /**
     * Process camera frame using TFLite pose detection model.
     * Converts ImageProxy to bitmap, resizes to model input, runs inference.
     */
    fun processFrame(imageProxy: ImageProxy) {
        if (!isRunning || isPaused) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            val inputBuffer = bitmapToInputBuffer(bitmap)

            // Output buffer: [1, numKeypoints, 3] for (y, x, confidence)
            val outputBuffer = Array(1) { Array(numKeypoints) { FloatArray(3) } }

            interpreter?.run(inputBuffer, outputBuffer)

            // Parse keypoints and detect action
            val keypoints = outputBuffer[0]
            val result = actionDetector?.detect(keypoints)
            
            if (result != null && result.isDetected) {
                count = result.count
                _countEvents.value = CountEvent(
                    count = count,
                    confidence = result.confidence
                )
            }


        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Convert ImageProxy (YUV_420_888) to Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
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
        val jpegBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Convert Bitmap to TFLite input buffer (normalized float values)
     */
    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(modelInputSize * modelInputSize)
        resizedBitmap.getPixels(intValues, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)

        for (pixelValue in intValues) {
            // Normalize to [-1, 1] or [0, 1] depending on model requirements
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((pixelValue and 0xFF) / 255.0f))
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    /**
     * Set the action type for detection (e.g., PUSH_UP, SQUAT, PLANK).
     * Must be called before start().
     */
    fun setActionType(actionType: ActionType) {
        currentActionType = actionType
    }
}
