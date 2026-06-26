package com.snuabar.counter.core.detection.tflite

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Helper for MediaPipe Pose Landmarker using .task model files.
 *
 * Encapsulates the full pipeline: person detection + 33 keypoint landmark detection.
 * Use IMAGE mode for synchronous single-frame detection.
 */
class PoseLandmarkerHelper(
    context: Context,
    modelFileName: String,
    private val runningMode: RunningMode = RunningMode.IMAGE,
    private val minPoseDetectionConfidence: Float = 0.5f,
    private val minPoseTrackingConfidence: Float = 0.5f,
    private val minPosePresenceConfidence: Float = 0.5f
) {
    private var poseLandmarker: PoseLandmarker? = null

    init {
        initPoseLandmarker(context, modelFileName)
    }

    private fun initPoseLandmarker(context: Context, modelFileName: String) {
        // Try GPU first, fallback to CPU if not supported
        poseLandmarker = try {
            createPoseLandmarker(context, modelFileName, Delegate.GPU)
        } catch (e: Exception) {
            android.util.Log.w("PoseLandmarker", "GPU delegate not supported or failed, falling back to CPU: ${e.message}")
            try {
                createPoseLandmarker(context, modelFileName, Delegate.CPU)
            } catch (e2: Exception) {
                android.util.Log.e("PoseLandmarker", "Failed to initialize with CPU delegate: ${e2.message}")
                null
            }
        }
    }

    private fun createPoseLandmarker(context: Context, modelFileName: String, delegate: Delegate): PoseLandmarker {
        val baseOptionsBuilder = BaseOptions.builder()
            .setDelegate(delegate)
            .setModelAssetPath(modelFileName)

        val baseOptions = baseOptionsBuilder.build()
        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinTrackingConfidence(minPoseTrackingConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setRunningMode(runningMode)

        val options = optionsBuilder.build()
        android.util.Log.i("PoseLandmarker", "Initialized with ${delegate.name} delegate, model=$modelFileName")
        return PoseLandmarker.createFromOptions(context, options)
    }

    /**
     * Detect pose landmarks from a single bitmap image.
     * Returns the first detected person's landmarks (33 keypoints) or null.
     */
    fun detect(bitmap: Bitmap): PoseLandmarkerResult? {
        if (poseLandmarker == null) return null
        val mpImage = BitmapImageBuilder(bitmap).build()
        return try {
            poseLandmarker!!.detect(mpImage)
        } catch (e: Exception) {
            android.util.Log.e("PoseLandmarker", "Detection failed: ${e.message}")
            null
        }
    }

    /**
     * Release resources.
     */
    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }
}
