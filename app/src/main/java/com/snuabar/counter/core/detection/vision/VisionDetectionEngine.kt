package com.snuabar.counter.core.detection.vision

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.snuabar.counter.core.detection.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.core.CvType
import org.opencv.core.Mat
import javax.inject.Inject

class VisionDetectionEngine @Inject constructor(
    private val context: Context
) : DetectionEngine, ImageAnalysis.Analyzer {

    private val _countEvents = MutableStateFlow(CountEvent(count = 0, confidence = 0f))
    override val countEvents: Flow<CountEvent> = _countEvents.asStateFlow()

    private val differencer = FrameDifferencer()
    private val motionDetector = MotionDetector()
    private var threshold = 0.7f
    private var isRunning = false
    private var isPaused = false

    override fun start(config: DetectionConfig) {
        if (isRunning) return
        isRunning = true
        isPaused = false
        threshold = config.threshold
        motionDetector.reset()
        differencer.reset()
        _countEvents.value = CountEvent(count = 0, confidence = 0f)
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
        differencer.reset()
        motionDetector.reset()
    }

    override fun setThreshold(threshold: Float) {
        this.threshold = threshold
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (!isRunning || isPaused) {
            imageProxy.close()
            return
        }

        try {
            val mat = imageProxyToGrayMat(imageProxy)
            val diff = differencer.computeDifference(mat)

            // threshold range 0-1, map to pixel value range 10-110
            val pixelThreshold = (10 + threshold * 100).toDouble()

            val detected = motionDetector.detect(diff, pixelThreshold)

            if (detected) {
                _countEvents.value = CountEvent(
                    count = motionDetector.getCount(),
                    confidence = 1.0f
                )
            }

            mat.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToGrayMat(imageProxy: ImageProxy): Mat {
        val yPlane = imageProxy.planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = imageProxy.width
        val height = imageProxy.height

        val mat = Mat(height, width, CvType.CV_8UC1)

        if (rowStride == width) {
            // No padding, fast path
            val bytes = ByteArray(width * height)
            yBuffer.get(bytes)
            mat.put(0, 0, bytes)
        } else {
            // Handle padding, copy row by row
            for (row in 0 until height) {
                yBuffer.position(row * rowStride)
                val rowBytes = ByteArray(width)
                yBuffer.get(rowBytes)
                mat.put(row, 0, rowBytes)
            }
        }

        return mat
    }
}
