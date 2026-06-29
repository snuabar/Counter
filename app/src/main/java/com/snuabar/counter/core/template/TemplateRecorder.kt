package com.snuabar.counter.core.template

import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType
import com.snuabar.counter.domain.model.PoseType
import com.snuabar.counter.domain.model.PoseDetector
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
    private var isPaused = false
    private var targetFrames = 0
    private var receivedFrames = 0
    private val collectedFeatures = mutableListOf<FloatArray>()
    private val collectedPoseTypes = mutableListOf<PoseType>()
    private val collectedRawKeypoints = mutableListOf<Array<FloatArray>>()

    // Progress callback: (currentFrame, targetFrames)
    var onProgressUpdate: ((Int, Int) -> Unit)? = null

    fun startRecording(durationSeconds: Int, fps: Int = 30) {
        collectedFeatures.clear()
        collectedRawKeypoints.clear()
        lastRecordedFeatures = null
        receivedFrames = 0
        isPaused = false
        targetFrames = durationSeconds * fps
        isRecording = true
    }

    // Last frame's features for real-time motion detection during recording
    private var lastRecordedFeatures: FloatArray? = null

    /**
     * Add a frame of keypoints (17 COCO keypoints, each [x, y, confidence]).
     * Frames with insufficient motion are skipped to avoid recording static poses.
     * Automatically pauses when target duration is reached.
     */
    fun addKeypoints(keypoints: Array<FloatArray>) {
        if (!isRecording || isPaused) return
        if (receivedFrames >= targetFrames + 60) return

        receivedFrames++
        onProgressUpdate?.invoke(receivedFrames, targetFrames)

        // Auto-pause when target duration is reached to prevent recording unwanted frames
        if (receivedFrames >= targetFrames) {
            isPaused = true
        }

        val features = extractAngleFeatures(keypoints) ?: return

        // Detect pose from keypoints
        val pose = PoseDetector.detectPose(keypoints)
        collectedPoseTypes.add(pose)

        // Save features for motion comparison in stopAndBuildTemplate
        lastRecordedFeatures = features.copyOf()
        collectedFeatures.add(features)
        collectedRawKeypoints.add(keypoints.map { it.copyOf() }.toTypedArray())
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
        val (bestStart, windowSize) = extractBestSegmentIndices(collectedFeatures)
        val bestFeatures = collectedFeatures.subList(bestStart, bestStart + windowSize)

        // Smooth features for longer templates to reduce MediaPipe jitter.
        // Fast actions (< 25 frames) skip smoothing to preserve sharp motion details.
        val smoothedFeatures = if (bestFeatures.size >= 25) {
            smoothFeatures(bestFeatures, windowSize = 3)
        } else {
            bestFeatures
        }

        // Check template quality: motion score
        val motionScore = computeMotionScore(smoothedFeatures)
        val MIN_MOTION = 0.05f
        if (motionScore < MIN_MOTION) {
            throw InsufficientMotionException(
                "动作幅度太小（${String.format("%.3f", motionScore)}），请重新录制，确保做完整的动作"
            )
        }

        // Encode the smoothed segment as ByteArray
        val featureBytes = encodeFeatureSequence(smoothedFeatures)

        // Encode the raw keypoints for preview animation
        val bestKeypoints = collectedRawKeypoints.subList(bestStart, bestStart + windowSize)
        val keypointBytes = encodeKeypointSequence(bestKeypoints)

        // Determine the most common pose type from recorded frames
        val detectedPoseType = if (collectedPoseTypes.isNotEmpty()) {
            collectedPoseTypes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: PoseType.UNKNOWN
        } else {
            PoseType.UNKNOWN
        }

        return Template(
            userId = userId,
            name = name,
            type = TemplateType.CUSTOM,
            sensorType = SensorType.VISION,
            featureVector = featureBytes,
            keypointSequence = keypointBytes,
            threshold = threshold,
            poseType = detectedPoseType
        )
    }

    /**
     * Extract the best segment by finding a single complete action around the motion peak.
     * Instead of searching all window sizes, this finds the highest-motion peak and expands
     * outward until motion drops below a threshold, capturing one full action cycle.
     * This avoids including multiple actions (e.g. 3 claps in 3 seconds) in the template.
     */
    private fun extractBestSegmentIndices(features: List<FloatArray>): Pair<Int, Int> {
        if (features.size < 15) return Pair(0, features.size)

        val totalFrames = features.size

        // Compute frame-to-frame motion for each frame (frame[0] has motion 0)
        val motions = FloatArray(totalFrames) { i ->
            if (i == 0) 0f else computeFrameMotion(features[i - 1], features[i])
        }

        // Find the frame with maximum motion (peak)
        var peakIndex = 0
        var peakMotion = -1f
        for (i in 1 until totalFrames) {
            if (motions[i] > peakMotion) {
                peakMotion = motions[i]
                peakIndex = i
            }
        }

        // Expand from peak until motion drops below threshold (30% of peak)
        // This captures one complete action cycle around the peak
        val threshold = peakMotion * 0.3f
        var start = peakIndex
        var end = peakIndex

        // Expand backward
        for (i in peakIndex - 1 downTo 1) {
            if (motions[i] < threshold) break
            start = i
        }

        // Expand forward
        for (i in peakIndex + 1 until totalFrames) {
            if (motions[i] < threshold) break
            end = i
        }

        // Add padding (2 frames before and after) for smoother detection matching
        start = kotlin.math.max(0, start - 2)
        end = kotlin.math.min(totalFrames - 1, end + 2)

        // Ensure reasonable bounds for the template length
        val minLength = 8   // ~0.5s at 15fps, enough for a quick action like clapping
        val maxLength = 35  // ~2.3s at 15fps, enough for a slow action like squat

        var length = end - start + 1

        // Extend if too short
        if (length < minLength) {
            val needed = minLength - length
            end = kotlin.math.min(totalFrames - 1, end + needed)
            length = end - start + 1
            if (length < minLength) {
                start = kotlin.math.max(0, start - (minLength - length))
                length = end - start + 1
            }
        }

        // Shrink if too long (trim equally from both sides, keeping peak centered)
        if (length > maxLength) {
            val excess = length - maxLength
            val trimStart = excess / 2
            val trimEnd = excess - trimStart
            start += trimStart
            end -= trimEnd
            length = end - start + 1
        }

        android.util.Log.d("TemplateRecorder",
            "Extracted best segment: start=$start, length=$length, peakMotion=${String.format("%.4f", peakMotion)}, totalFrames=$totalFrames")

        return Pair(start, length)
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

    /**
     * Smooth feature sequence using sliding average to reduce MediaPipe detection jitter.
     * Each dimension is smoothed independently with a symmetric window.
     * Edge frames use reduced window (replicate padding).
     * @param windowSize must be odd (3, 5, 7...)
     */
    private fun smoothFeatures(features: List<FloatArray>, windowSize: Int = 3): List<FloatArray> {
        if (features.size < windowSize || windowSize < 2) return features

        val halfWindow = windowSize / 2
        val featureDim = features[0].size
        val result = mutableListOf<FloatArray>()

        for (i in features.indices) {
            val smoothed = FloatArray(featureDim)
            for (d in 0 until featureDim) {
                var sum = 0f
                var count = 0
                for (j in -halfWindow..halfWindow) {
                    val idx = i + j
                    if (idx in features.indices) {
                        sum += features[idx][d]
                        count++
                    }
                }
                smoothed[d] = sum / count
            }
            result.add(smoothed)
        }

        return result
    }

    fun cancelRecording() {
        isRecording = false
        collectedFeatures.clear()
        collectedPoseTypes.clear()
        collectedRawKeypoints.clear()
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

        // Check all required keypoints including wrist and ankle for consistent feature extraction
        val leftAnkle = if (keypoints.size > 15) keypoints[15] else return null
        val rightAnkle = if (keypoints.size > 16) keypoints[16] else return null
        val leftWrist = if (keypoints.size > 9) keypoints[9] else return null
        val rightWrist = if (keypoints.size > 10) keypoints[10] else return null

        val required = arrayOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle, leftWrist, rightWrist)
        if (required.any { it.size < 3 || it[2] < minConfidence }) {
            android.util.Log.w("TemplateRecorder", "Some keypoints below confidence threshold, skipping frame")
            return null
        }

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

        // Extended features for action differentiation (组合策略)
        // Feature 9: kneeAngleSum - sum of both knee angles, smaller when squatting deeper
        val kneeAngleSum = (leftKneeAngle + rightKneeAngle) / 360f
        // Feature 10: bodyHeight - vertical distance from shoulder center to ankle center
        val shoulderCenterY = (leftShoulder[1] + rightShoulder[1]) / 2f
        val ankleCenterY = (leftAnkle[1] + rightAnkle[1]) / 2f
        val bodyHeight = shoulderCenterY - ankleCenterY
        // Feature 11: ankleY - average ankle vertical position
        val ankleY = (leftAnkle[1] + rightAnkle[1]) / 2f

        // Feature 12: hand distance ratio - distinguishes clapping (hands together) from waving (hands apart)
        // Uses index finger tips (keypoints[3] and keypoints[4]) for best hand contact detection
        val leftIndexX = keypoints[3][0]
        val leftIndexY = keypoints[3][1]
        val rightIndexX = keypoints[4][0]
        val rightIndexY = keypoints[4][1]
        val handDistance = kotlin.math.sqrt(
            (leftIndexX - rightIndexX) * (leftIndexX - rightIndexX) +
            (leftIndexY - rightIndexY) * (leftIndexY - rightIndexY)
        )
        val shoulderWidth = kotlin.math.abs(leftShoulder[0] - rightShoulder[0])
        val handDistanceRatio = handDistance / (shoulderWidth + 1e-6f)

        return floatArrayOf(
            leftElbowAngle / 180f,
            rightElbowAngle / 180f,
            leftShoulderAngle / 180f,
            rightShoulderAngle / 180f,
            leftHipAngle / 180f,
            rightHipAngle / 180f,
            leftKneeAngle / 180f,
            rightKneeAngle / 180f,
            hipCenterY,            // 9th dim: hip center vertical position [0,1]
            kneeAngleSum,          // 10th dim: knee angle sum [0,1], smaller when deeper squat
            bodyHeight,            // 11th dim: body height (shoulder to ankle)
            ankleY,                // 12th dim: average ankle Y position
            handDistanceRatio      // 13th dim: hand distance / shoulder width (0=clapping, 1=waving)
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

    /**
     * Encode a sequence of raw keypoints into a ByteArray.
     * Format: [4 bytes frame count][4 bytes keypoint count per frame][keypoint data...]
     * Each keypoint: [x, y, confidence] as 3 floats (12 bytes)
     */
    private fun encodeKeypointSequence(keypoints: List<Array<FloatArray>>): ByteArray {
        if (keypoints.isEmpty()) return ByteArray(0)

        val frameCount = keypoints.size
        val keypointCount = keypoints.first().size
        val valuesPerKeypoint = 3 // x, y, confidence

        val totalBytes = 8 + frameCount * keypointCount * valuesPerKeypoint * 4
        val bytes = ByteArray(totalBytes)

        // Write header
        writeInt(bytes, 0, frameCount)
        writeInt(bytes, 4, keypointCount)

        // Write data
        var offset = 8
        for (frame in keypoints) {
            for (kp in frame) {
                for (i in 0 until valuesPerKeypoint) {
                    writeFloat(bytes, offset, if (i < kp.size) kp[i] else 0f)
                    offset += 4
                }
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
