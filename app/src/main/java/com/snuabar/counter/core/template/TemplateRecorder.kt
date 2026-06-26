package com.snuabar.counter.core.template

import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Records keypoints over a duration and builds a template feature vector.
 *
 * Usage:
 * 1. Call startRecording(durationSeconds)
 * 2. Feed keypoints via addKeypoints() for each frame
 * 3. Call stopAndBuildTemplate() to compute and return the feature vector
 */
class TemplateRecorder {

    private var isRecording = false
    private var targetFrames = 0
    private val collectedFeatures = mutableListOf<FloatArray>()

    // Progress callback: (currentFrame, targetFrames)
    var onProgressUpdate: ((Int, Int) -> Unit)? = null

    fun startRecording(durationSeconds: Int, fps: Int = 10) {
        collectedFeatures.clear()
        lastRecordedFeatures = null
        targetFrames = durationSeconds * fps
        isRecording = true
    }

    // Last frame's features for real-time motion detection during recording
    private var lastRecordedFeatures: FloatArray? = null

    /**
     * Add a frame of keypoints (17 COCO keypoints, each [x, y, confidence]).
     * Frames with insufficient motion are skipped to avoid recording static poses.
     */
    fun addKeypoints(keypoints: Array<FloatArray>) {
        if (!isRecording) return
        if (collectedFeatures.size >= targetFrames) return

        val features = extractAngleFeatures(keypoints) ?: return

        // Real-time motion detection: skip frames where the person is not moving enough.
        // This prevents the progress bar from advancing when standing still.
        val last = lastRecordedFeatures
        if (last != null) {
            val motion = computeFrameMotion(last, features)
            if (motion < 0.05f) {
                // Too little motion, skip this frame (but keep recording)
                return
            }
        }

        lastRecordedFeatures = features.copyOf()
        collectedFeatures.add(features)
        onProgressUpdate?.invoke(collectedFeatures.size, targetFrames)
    }

    /**
     * Exception thrown when the recorded template has insufficient motion.
     * This indicates the user stood still during recording.
     */
    class InsufficientMotionException(message: String) : Exception(message)

    /**
     * Stop recording and build the template feature vector (as ByteArray).
     * Returns null if not enough frames were collected.
     * Throws InsufficientMotionException if the motion is too small (user stood still).
     */
    fun stopAndBuildTemplate(
        name: String,
        userId: Long? = null,
        threshold: Float = 0.7f
    ): Template? {
        isRecording = false

        if (collectedFeatures.isEmpty()) return null

        // Extract the best segment (highest motion) to remove "dead frames"
        // where the user is walking to/from the phone. This keeps only the
        // frames where the actual action is happening.
        val bestFeatures = extractBestSegment(collectedFeatures)

        // Check template quality: motion score
        val motionScore = computeMotionScore(bestFeatures)
        val MIN_MOTION = 0.05f
        if (motionScore < MIN_MOTION) {
            throw InsufficientMotionException(
                "动作幅度太小（${String.format("%.3f", motionScore)}），请重新录制，确保做完整的动作"
            )
        }

        // Encode the best segment as ByteArray
        val featureBytes = encodeFeatureSequence(bestFeatures)

        return Template(
            userId = userId,
            name = name,
            type = TemplateType.CUSTOM,
            sensorType = SensorType.VISION,
            featureVector = featureBytes,
            threshold = threshold
        )
    }

    /**
     * Extract the best segment (highest motion) from the recorded frames.
     * This removes "dead frames" at the beginning and end where the user
     * is walking to/from the phone.
     *
     * Uses a sliding window to find the segment with the highest motion score.
     * The window size is 70% of the total frames (e.g., 21 frames out of 30).
     */
    private fun extractBestSegment(features: List<FloatArray>): List<FloatArray> {
        if (features.size < 10) return features

        val totalFrames = features.size
        // Window size: 70% of total frames, at least 10 frames
        val windowSize = kotlin.math.max(10, (totalFrames * 0.7).toInt())

        if (windowSize >= totalFrames) return features

        var bestScore = -1f
        var bestStart = 0

        // Sliding window: find the segment with highest motion score
        for (i in 0..totalFrames - windowSize) {
            val segment = features.subList(i, i + windowSize)
            val score = computeMotionScore(segment)
            if (score > bestScore) {
                bestScore = score
                bestStart = i
            }
        }

        return features.subList(bestStart, bestStart + windowSize)
    }

    /**
     * Compute the motion score of a feature sequence.
     * This is the average distance from each frame to the mean.
     * Low score means the user stood still.
     */
    private fun computeMotionScore(features: List<FloatArray>): Float {
        if (features.size < 2) return 0f
        val frameCount = features.size
        val featureDim = features[0].size

        // Compute mean for each dimension
        val mean = FloatArray(featureDim) { d ->
            features.sumOf { it[d].toDouble() }.toFloat() / frameCount
        }

        // Compute average distance from each frame to the mean
        var totalDist = 0f
        for (frame in features) {
            var distSq = 0f
            for (d in 0 until featureDim) {
                val diff = frame[d] - mean[d]
                distSq += diff * diff
            }
            totalDist += kotlin.math.sqrt(distSq)
        }

        return totalDist / frameCount
    }

    fun cancelRecording() {
        isRecording = false
        collectedFeatures.clear()
        lastRecordedFeatures = null
    }

    val recordedFrames: Int get() = collectedFeatures.size
    val totalTargetFrames: Int get() = targetFrames
    val recording: Boolean get() = isRecording

    /**
     * Extract 9 features from 17 COCO keypoints.
     * Same logic as CustomPoseActionDetector.extractAngleFeatures.
     */
    private fun extractAngleFeatures(keypoints: Array<FloatArray>): FloatArray? {
        if (keypoints.size < 17) {
            android.util.Log.w("TemplateRecorder", "Keypoints size ${keypoints.size} < 17, skipping frame")
            return null
        }

        val minConfidence = 0.3f
        val leftShoulder = keypoints[5]
        val rightShoulder = keypoints[6]
        val leftElbow = keypoints[7]
        val rightElbow = keypoints[8]
        val leftHip = keypoints[11]
        val rightHip = keypoints[12]
        val leftKnee = keypoints[13]
        val rightKnee = keypoints[14]

        val required = arrayOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftHip, rightHip, leftKnee, rightKnee)
        if (required.any { it.size < 3 || it[2] < minConfidence }) {
            android.util.Log.w("TemplateRecorder", "Some keypoints below confidence threshold, skipping frame")
            return null
        }

        // Use ankles/wrists if visible, otherwise fallback to knees/elbows for angle calculation
        val leftAnkle = if (keypoints.size > 15 && keypoints[15].size >= 3 && keypoints[15][2] >= minConfidence) keypoints[15] else leftKnee
        val rightAnkle = if (keypoints.size > 16 && keypoints[16].size >= 3 && keypoints[16][2] >= minConfidence) keypoints[16] else rightKnee
        val leftWrist = if (keypoints.size > 9 && keypoints[9].size >= 3 && keypoints[9][2] >= minConfidence) keypoints[9] else leftElbow
        val rightWrist = if (keypoints.size > 10 && keypoints[10].size >= 3 && keypoints[10][2] >= minConfidence) keypoints[10] else rightElbow

        val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
        val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
        val leftShoulderAngle = calculateAngle(leftElbow, leftShoulder, leftHip)
        val rightShoulderAngle = calculateAngle(rightElbow, rightShoulder, rightHip)
        val leftHipAngle = calculateAngle(leftShoulder, leftHip, leftKnee)
        val rightHipAngle = calculateAngle(rightShoulder, rightHip, rightKnee)
        val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)

        // Hip center vertical position: helps distinguish squat from side-to-side movement
        val hipCenterY = (leftHip[1] + rightHip[1]) / 2f

        return floatArrayOf(
            leftElbowAngle / 180f,
            rightElbowAngle / 180f,
            leftShoulderAngle / 180f,
            rightShoulderAngle / 180f,
            leftHipAngle / 180f,
            rightHipAngle / 180f,
            leftKneeAngle / 180f,
            rightKneeAngle / 180f,
            hipCenterY // 9th dim: hip center vertical position [0,1]
        )
    }

    /**
     * Compute Euclidean distance between two feature vectors.
     */
    private fun computeFrameMotion(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return kotlin.math.sqrt(sum)
    }

    private fun calculateAngle(p1: FloatArray, center: FloatArray, p2: FloatArray): Float {
        val angle1 = atan2(p1[1] - center[1], p1[0] - center[0])
        val angle2 = atan2(p2[1] - center[1], p2[0] - center[0])
        var angle = abs(angle1 - angle2) * 180f / PI.toFloat()
        if (angle > 180f) angle = 360f - angle
        return angle
    }

    /**
     * Encode a sequence of feature vectors into a ByteArray.
     * Format: [4 bytes frame count][4 bytes feature dim][frame data...]
     */
    private fun encodeFeatureSequence(sequences: List<FloatArray>): ByteArray {
        if (sequences.isEmpty()) return ByteArray(0)

        val frameCount = sequences.size
        val featureDim = sequences.first().size

        val totalBytes = 8 + frameCount * featureDim * 4
        val bytes = ByteArray(totalBytes)

        // Write header
        writeInt(bytes, 0, frameCount)
        writeInt(bytes, 4, featureDim)

        // Write data
        var offset = 8
        for (frame in sequences) {
            for (value in frame) {
                writeFloat(bytes, offset, value)
                offset += 4
            }
        }

        return bytes
    }

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeFloat(bytes: ByteArray, offset: Int, value: Float) {
        val bits = value.toBits()
        writeInt(bytes, offset, bits)
    }
}
