package com.snuabar.counter.core.detection.tflite

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.snuabar.counter.core.detection.CountEvent
import com.snuabar.counter.core.detection.DetectionConfig
import com.snuabar.counter.core.detection.DetectionEngine
import com.snuabar.counter.core.detection.tflite.action.ActionDetectorFactory
import com.snuabar.counter.core.detection.tflite.action.PoseActionDetector
import com.snuabar.counter.core.template.RecordingSession
import com.snuabar.counter.data.local.prefs.DetectionPreferences
import com.snuabar.counter.domain.model.ActionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * TFLite Detection Engine using MediaPipe PoseLandmarker for pose detection.
 *
 * Supports 3 model variants (lite/full/heavy) via .task files.
 * Outputs 17 COCO keypoints mapped from MediaPipe's 33 landmarks.
 */
class TFLiteDetectionEngine @Inject constructor(
    private val context: Context,
    private val recordingSession: RecordingSession,
    private val detectionPreferences: DetectionPreferences
) : DetectionEngine {

    private val _countEvents = MutableStateFlow(CountEvent(count = 0, confidence = 0f))
    override val countEvents: Flow<CountEvent> = _countEvents.asStateFlow()

    // Keypoints for visualization: array of [y, x, confidence] for each keypoint
    private val _keypoints = MutableStateFlow<Array<FloatArray>?>(null)
    val keypoints: Flow<Array<FloatArray>?> = _keypoints.asStateFlow()

    // FPS tracking: actual inference frequency
    private val _fps = MutableStateFlow(0)
    val fps: Flow<Int> = _fps.asStateFlow()
    private val fpsTimestamps = ArrayDeque<Long>()

    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var isFrontCamera = false
    private var threshold = 0.7f
    private var isRunning = false
    private var isPaused = false
    private var count = 0

    // Smoothing state to reduce jitter
    private var previousKeypoints: Array<FloatArray>? = null
    private val smoothingAlpha = 0.4f

    override fun isRunning(): Boolean = isRunning

    override fun setCameraInfo(isFrontCamera: Boolean) {
        this.isFrontCamera = isFrontCamera
        android.util.Log.d("TFLite", "Camera info set: isFrontCamera=$isFrontCamera")
    }

    // Throttle frame processing to max ~10 fps (same as counting page)
    private var lastAnalyzeTime = 0L

    private val numKeypoints = 17     // COCO format: nose, eyes, ears, shoulders, elbows, wrists, hips, knees, ankles

    // Action detection
    private var actionDetector: PoseActionDetector? = null
    private var currentActionType: ActionType = ActionType.CUSTOM

    // Async analysis executor to avoid blocking the caller thread
    // Queue size=1: discard old tasks, always process the latest frame
    private var analysisExecutor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(1),
        ThreadPoolExecutor.DiscardPolicy()
    )
    @Volatile
    private var isAnalyzing = false

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

        // Ensure executor is not shutdown
        if (analysisExecutor.isShutdown) {
            analysisExecutor = ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(1),
                ThreadPoolExecutor.DiscardPolicy()
            )
        }

        // Load PoseLandmarker model
        initPoseLandmarker(config.poseModelConfig.fileName)

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

        // Ensure executor is not shutdown
        if (analysisExecutor.isShutdown) {
            analysisExecutor = ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(1),
                ThreadPoolExecutor.DiscardPolicy()
            )
        }

        initPoseLandmarker(config.poseModelConfig.fileName)
        // No action detector - just inference for skeleton
    }

    private fun initPoseLandmarker(modelPath: String) {
        try {
            val useGpu = runBlocking { detectionPreferences.gpuAccelerationFlow.first() }
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = context,
                modelFileName = modelPath,
                minPoseDetectionConfidence = 0.5f,
                minPoseTrackingConfidence = 0.5f,
                minPosePresenceConfidence = 0.5f,
                useGpu = useGpu
            )
            android.util.Log.d("TFLite", "PoseLandmarker initialized: $modelPath (GPU=$useGpu)")
        } catch (e: Exception) {
            android.util.Log.e("TFLite", "PoseLandmarker init failed: ${e.message}")
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
            poseLandmarkerHelper?.close()
            poseLandmarkerHelper = null
        }
        actionDetector?.reset()
        actionDetector = null
        _keypoints.value = null
        previousKeypoints = null
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
     * Process a bitmap directly (for Camera2 or other sources).
     * Runs inference asynchronously on analysisExecutor to avoid blocking the caller thread.
     */
    fun processBitmap(bitmap: Bitmap) {
        if (!isRunning || isPaused) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }

        // Check if executor queue is full to avoid memory pressure
        if (analysisExecutor.queue.remainingCapacity() <= 0) {
            android.util.Log.w("TFLite", "Executor queue full, dropping frame")
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }

        analysisExecutor.execute {
            try {
                if (!isRunning || isPaused) return@execute
                    if (poseLandmarkerHelper != null) {
                        runPoseLandmarkerInference(bitmap)
                    } else {
                        android.util.Log.w("TFLite", "processBitmap: no PoseLandmarker available")
                }
            } catch (e: Exception) {
                android.util.Log.e("TFLite", "processBitmap error: ${e.message}", e)
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun runPoseLandmarkerInference(bitmap: Bitmap) {
        val inferenceStart = System.currentTimeMillis()
        android.util.Log.d("TFLite", "runPoseLandmarkerInference called, bitmap=${bitmap.width}x${bitmap.height}")

        val detectStart = System.currentTimeMillis()
        val result = poseLandmarkerHelper?.detect(bitmap, detectStart)
        val detectTime = System.currentTimeMillis() - detectStart
        android.util.Log.d("TFLite", "PoseLandmarker detect took ${detectTime}ms")

        if (result == null || result.landmarks().isEmpty()) {
            android.util.Log.d("TFLite", "No person detected by PoseLandmarker")
            return
        }

        // Get first person's 33 landmarks
        val landmarks33 = result.landmarks()[0]
        android.util.Log.d("TFLite", "PoseLandmarker returned ${landmarks33.size} landmarks")

        // Map 33 MediaPipe landmarks to 17 COCO keypoints
        val keypoints17 = mapMediaPipe33ToCoco17(landmarks33)
        android.util.Log.d("TFLite", "mapMediaPipe33ToCoco17 returned ${keypoints17.size} keypoints")

        // Process as usual
        processKeypoints(keypoints17)
        val totalTime = System.currentTimeMillis() - inferenceStart
        android.util.Log.d("TFLite", "Total inference pipeline took ${totalTime}ms")
    }

    private fun processKeypoints(keypoints: Array<FloatArray>) {
        android.util.Log.d("TFLite", "processKeypoints: ${keypoints.size} keypoints, first=${keypoints.firstOrNull()?.contentToString()}")

        // Update FPS counter
        val now = System.currentTimeMillis()
        fpsTimestamps.addLast(now)
        while (fpsTimestamps.isNotEmpty() && now - fpsTimestamps.first() > 1000L) {
            fpsTimestamps.removeFirst()
        }
        _fps.value = fpsTimestamps.size

        // Apply smoothing to reduce jitter
        val prev = previousKeypoints
        val smoothedKeypoints = if (prev == null) {
            val newPrev = Array(keypoints.size) { FloatArray(3) }
            for (i in keypoints.indices) {
                newPrev[i][0] = keypoints[i][0]
                newPrev[i][1] = keypoints[i][1]
                newPrev[i][2] = keypoints[i][2]
            }
            previousKeypoints = newPrev
            keypoints
        } else {
            for (i in keypoints.indices) {
                prev[i][0] = smoothingAlpha * keypoints[i][0] + (1 - smoothingAlpha) * prev[i][0]
                prev[i][1] = smoothingAlpha * keypoints[i][1] + (1 - smoothingAlpha) * prev[i][1]
                prev[i][2] = keypoints[i][2]
            }
            previousKeypoints = prev
            prev
        }

        // Store keypoints for visualization
        _keypoints.value = smoothedKeypoints

        // Forward keypoints to recording session if active
        if (recordingSession.isRecording()) {
            recordingSession.onKeypointsDetected(smoothedKeypoints)
        }

        val result = actionDetector?.detect(smoothedKeypoints)

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
     * Map 33 MediaPipe landmarks to 17 COCO keypoints.
     * Returns [x, y, confidence] for each keypoint.
     */
    private fun mapMediaPipe33ToCoco17(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Array<FloatArray> {
        val coco17 = Array(17) { FloatArray(3) }

        // Helper: average multiple MediaPipe landmarks
        fun avg(vararg indices: Int): FloatArray {
            var sumX = 0f
            var sumY = 0f
            var sumConf = 0f
            for (idx in indices) {
                sumX += landmarks[idx].x()
                sumY += landmarks[idx].y()
                sumConf += landmarks[idx].visibility().orElse(0f)
            }
            val count = indices.size.toFloat()
            return floatArrayOf(sumX / count, sumY / count, sumConf / count)
        }

        // Map each COCO keypoint (MediaPipe 33 → COCO 17)
        coco17[0]  = floatArrayOf(landmarks[0].x(), landmarks[0].y(), landmarks[0].visibility().orElse(0f)) // nose → nose
        coco17[1]  = avg(1, 2, 3) // left_eye → avg of left_eye_inner, left_eye, left_eye_outer
        coco17[2]  = avg(4, 5, 6) // right_eye → avg of right_eye_inner, right_eye, right_eye_outer
        coco17[3]  = floatArrayOf(landmarks[7].x(), landmarks[7].y(), landmarks[7].visibility().orElse(0f)) // left_ear
        coco17[4]  = floatArrayOf(landmarks[8].x(), landmarks[8].y(), landmarks[8].visibility().orElse(0f)) // right_ear
        coco17[5]  = floatArrayOf(landmarks[11].x(), landmarks[11].y(), landmarks[11].visibility().orElse(0f)) // left_shoulder
        coco17[6]  = floatArrayOf(landmarks[12].x(), landmarks[12].y(), landmarks[12].visibility().orElse(0f)) // right_shoulder
        coco17[7]  = floatArrayOf(landmarks[13].x(), landmarks[13].y(), landmarks[13].visibility().orElse(0f)) // left_elbow
        coco17[8]  = floatArrayOf(landmarks[14].x(), landmarks[14].y(), landmarks[14].visibility().orElse(0f)) // right_elbow
        coco17[9]  = floatArrayOf(landmarks[15].x(), landmarks[15].y(), landmarks[15].visibility().orElse(0f)) // left_wrist
        coco17[10] = floatArrayOf(landmarks[16].x(), landmarks[16].y(), landmarks[16].visibility().orElse(0f)) // right_wrist
        coco17[11] = floatArrayOf(landmarks[23].x(), landmarks[23].y(), landmarks[23].visibility().orElse(0f)) // left_hip
        coco17[12] = floatArrayOf(landmarks[24].x(), landmarks[24].y(), landmarks[24].visibility().orElse(0f)) // right_hip
        coco17[13] = floatArrayOf(landmarks[25].x(), landmarks[25].y(), landmarks[25].visibility().orElse(0f)) // left_knee
        coco17[14] = floatArrayOf(landmarks[26].x(), landmarks[26].y(), landmarks[26].visibility().orElse(0f)) // right_knee
        coco17[15] = floatArrayOf(landmarks[27].x(), landmarks[27].y(), landmarks[27].visibility().orElse(0f)) // left_ankle
        coco17[16] = floatArrayOf(landmarks[28].x(), landmarks[28].y(), landmarks[28].visibility().orElse(0f)) // right_ankle

        return coco17
    }
}
