package com.snuabar.counter.core.detection.tflite.action

import com.snuabar.counter.domain.model.ActionType
import com.snuabar.counter.domain.model.Template
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Custom pose action detector using template matching with DTW.
 *
 * Extracts joint angle feature vectors from keypoints and compares
 * against a stored template using Dynamic Time Warping.
 *
 * COCO keypoints of interest:
 * - 5: left_shoulder, 6: right_shoulder
 * - 7: left_elbow, 8: right_elbow
 * - 11: left_hip, 12: right_hip
 * - 13: left_knee, 14: right_knee
 */
class CustomPoseActionDetector(
    private val template: Template
) : BasePoseActionDetector(ActionType.CUSTOM) {

    // Sliding window of recent feature vectors
    private val windowSize = 16
    private val featureWindow = ArrayDeque<FloatArray>()

    // Template feature vector (decoded from bytes)
    private val templateFeatures: FloatArray = decodeFeatureVector(template.featureVector)
    private val matchThreshold: Float = template.threshold

    // Cooldown frames to prevent double-counting
    private val customCooldownFrames = 15

    override fun reset() {
        super.reset()
        featureWindow.clear()
    }

    override fun detect(keypoints: Array<FloatArray>): PoseActionResult? {
        updateCooldown()

        // Extract current frame's angle features
        val currentFeatures = extractAngleFeatures(keypoints) ?: return PoseActionResult(
            actionType = ActionType.CUSTOM,
            isDetected = false,
            currentState = currentState,
            count = count,
            confidence = 0f,
            debugInfo = "Keypoints not visible"
        )

        // Add to sliding window
        featureWindow.addLast(currentFeatures)
        if (featureWindow.size > windowSize) {
            featureWindow.removeFirst()
        }

        // Need enough frames for meaningful comparison
        if (featureWindow.size < 4) {
            return PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = currentState,
                count = count,
                confidence = 0f,
                debugInfo = "Collecting frames: ${featureWindow.size}/4"
            )
        }

        // Compute average feature from window
        val avgFeatures = computeAverageFeatures()

        // Compare with template using DTW
        val score = computeDTWDistance(avgFeatures, templateFeatures)

        // Convert distance to similarity score (lower distance = higher similarity)
        val similarity = 1f / (1f + score)

        confidence = similarity

        return if (similarity >= matchThreshold && !isInCooldown()) {
            count++
            cooldownCounter = customCooldownFrames
            currentState = ActionState.COMPLETED
            PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = true,
                currentState = currentState,
                count = count,
                confidence = similarity,
                debugInfo = "Action detected! similarity=$similarity"
            )
        } else {
            currentState = if (similarity >= matchThreshold * 0.7f) ActionState.IN_PROGRESS else ActionState.IDLE
            PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = currentState,
                count = count,
                confidence = similarity,
                debugInfo = "similarity=$similarity, threshold=$matchThreshold"
            )
        }
    }

    /**
     * Extract angle features from 17 COCO keypoints.
     * Computes angles at: shoulders, elbows, hips, knees.
     * Returns a FloatArray of 8 angle values (in radians, normalized).
     */
    fun extractAngleFeatures(keypoints: Array<FloatArray>): FloatArray? {
        val minConfidence = 0.3f

        // Key indices
        val leftShoulder = keypoints[5]
        val rightShoulder = keypoints[6]
        val leftElbow = keypoints[7]
        val rightElbow = keypoints[8]
        val leftHip = keypoints[11]
        val rightHip = keypoints[12]
        val leftKnee = keypoints[13]
        val rightKnee = keypoints[14]

        // Check visibility of all required keypoints
        val required = arrayOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftHip, rightHip, leftKnee, rightKnee)
        if (required.any { !isVisible(it, minConfidence) }) return null

        // Compute joint angles (in degrees, then normalize to [0,1])
        val leftElbowAngle = calculateAngle(
            leftShoulder[0], leftShoulder[1],
            leftElbow[0], leftElbow[1],
            keypoints[9][0], keypoints[9][1] // left_wrist
        )
        val rightElbowAngle = calculateAngle(
            rightShoulder[0], rightShoulder[1],
            rightElbow[0], rightElbow[1],
            keypoints[10][0], keypoints[10][1] // right_wrist
        )
        val leftShoulderAngle = calculateAngle(
            leftElbow[0], leftElbow[1],
            leftShoulder[0], leftShoulder[1],
            leftHip[0], leftHip[1]
        )
        val rightShoulderAngle = calculateAngle(
            rightElbow[0], rightElbow[1],
            rightShoulder[0], rightShoulder[1],
            rightHip[0], rightHip[1]
        )
        val leftHipAngle = calculateAngle(
            leftShoulder[0], leftShoulder[1],
            leftHip[0], leftHip[1],
            leftKnee[0], leftKnee[1]
        )
        val rightHipAngle = calculateAngle(
            rightShoulder[0], rightShoulder[1],
            rightHip[0], rightHip[1],
            rightKnee[0], rightKnee[1]
        )
        val leftKneeAngle = calculateAngle(
            leftHip[0], leftHip[1],
            leftKnee[0], leftKnee[1],
            keypoints[15][0], keypoints[15][1] // left_ankle
        )
        val rightKneeAngle = calculateAngle(
            rightHip[0], rightHip[1],
            rightKnee[0], rightKnee[1],
            keypoints[16][0], keypoints[16][1] // right_ankle
        )

        // Normalize angles to [0, 1] range (divide by 180)
        return floatArrayOf(
            leftElbowAngle / 180f,
            rightElbowAngle / 180f,
            leftShoulderAngle / 180f,
            rightShoulderAngle / 180f,
            leftHipAngle / 180f,
            rightHipAngle / 180f,
            leftKneeAngle / 180f,
            rightKneeAngle / 180f
        )
    }

    private fun computeAverageFeatures(): FloatArray {
        val featureDim = featureWindow.first().size
        val avg = FloatArray(featureDim)
        for (features in featureWindow) {
            for (i in features.indices) {
                avg[i] += features[i]
            }
        }
        for (i in avg.indices) {
            avg[i] = avg[i] / featureWindow.size.toFloat()
        }
        return avg
    }

    /**
     * Simplified DTW distance between two feature sequences.
     * Since we compare averaged features vs template, this is essentially
     * Euclidean distance with a sliding window DTW for temporal alignment.
     */
    private fun computeDTWDistance(sequence: FloatArray, templateSeq: FloatArray): Float {
        // If template is a single feature vector (same dimension), use Euclidean distance
        if (sequence.size == templateSeq.size) {
            return euclideanDistance(sequence, templateSeq)
        }

        // DTW between two sequences of different lengths
        val n = sequence.size
        val m = templateSeq.size
        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0f

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = abs(sequence[i - 1] - templateSeq[j - 1])
                dtw[i][j] = cost + minOf(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
            }
        }

        return dtw[n][m] / maxOf(n, m)
    }

    private fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Decode feature vector from ByteArray (stored as float bytes).
     */
    private fun decodeFeatureVector(bytes: ByteArray?): FloatArray {
        if (bytes == null || bytes.isEmpty()) return FloatArray(8)
        val floatArray = FloatArray(bytes.size / 4)
        for (i in floatArray.indices) {
            val bits = (bytes[i * 4].toInt() and 0xFF) or
                    ((bytes[i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[i * 4 + 3].toInt() and 0xFF) shl 24)
            floatArray[i] = Float.fromBits(bits)
        }
        return floatArray
    }
}
