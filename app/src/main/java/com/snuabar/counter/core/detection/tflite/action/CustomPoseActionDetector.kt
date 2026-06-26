package com.snuabar.counter.core.detection.tflite.action

import com.snuabar.counter.domain.model.ActionType
import com.snuabar.counter.domain.model.Template
import java.util.Locale
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
    private val featureWindow = ArrayDeque<FloatArray>()

    // Template feature sequence (decoded from bytes)
    private val templateFeatures: List<FloatArray> = decodeFeatureSequence(template.featureVector)
    private val matchThreshold: Float = template.threshold

    // Window size matches template length for DTW comparison
    private val windowSize = templateFeatures.size.coerceIn(8, 60)

    init {
        android.util.Log.d("CustomPoseActionDetector", "Loaded template: name=${template.name}, featureVectorSize=${template.featureVector?.size ?: 0}, decodedFrames=${templateFeatures.size}, windowSize=$windowSize")
        if (templateFeatures.isEmpty()) {
            android.util.Log.e("CustomPoseActionDetector", "WARNING: Template has no feature vectors! featureVector=${template.featureVector?.size ?: 0} bytes")
        }
    }

    // Cooldown frames to prevent double-counting
    private val customCooldownFrames = 15
    private var lastSimilarity: Float = 0f

    // Minimum movement threshold: if feature window variance is below this,
    // the person is considered stationary and no counting occurs.
    // This is the average distance from each frame to the window mean.
    // Standing still with jitter: ~0.03-0.05; Actual movement: ~0.15+
    private val MIN_MOVEMENT_THRESHOLD = 0.05f

    // Dominant feature: the dimension with the largest variance in the template.
    // This is used for periodicity detection.
    private val dominantFeature: Int = computeDominantFeature(templateFeatures)

    // Minimum periodicity score: the dominant feature must show clear up-down pattern
    private val MIN_PERIODICITY_SCORE = 0.15f

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
            debugInfo = "Keypoints not visible",
            structuredDebugInfo = DetectionDebugInfo(
                state = currentState, confidence = 0f, message = "Keypoints not visible"
            )
        )

        // Add to sliding window
        featureWindow.addLast(currentFeatures)
        if (featureWindow.size > windowSize) {
            featureWindow.removeFirst()
        }

        // Need enough frames for comparison
        if (featureWindow.size < windowSize / 2) {
            return PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = currentState,
                count = count,
                confidence = 0f,
                debugInfo = "Collecting frames: ${featureWindow.size}/$windowSize",
                structuredDebugInfo = DetectionDebugInfo(
                    state = currentState, confidence = 0f,
                    message = "Collecting frames: ${featureWindow.size}/$windowSize"
                )
            )
        }

        // Check if person is actually moving (not standing still)
        val movementScore = computeMovementScore(featureWindow)
        if (movementScore < MIN_MOVEMENT_THRESHOLD) {
            android.util.Log.d("CustomPoseActionDetector", "Movement too low (score=$movementScore), skipping")
            return PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = ActionState.IDLE,
                count = count,
                confidence = 0f,
                debugInfo = "No movement (score=${String.format(Locale.getDefault(), "%.3f", movementScore)})",
                structuredDebugInfo = DetectionDebugInfo(
                    state = ActionState.IDLE, confidence = 0f,
                    message = "No movement (score=${String.format(Locale.getDefault(), "%.3f", movementScore)})"
                )
            )
        }

        // Check periodicity: the dominant feature must show clear up-down pattern
        val periodicityScore = computePeriodicityScore(featureWindow, dominantFeature)
        if (periodicityScore < MIN_PERIODICITY_SCORE) {
            android.util.Log.d("CustomPoseActionDetector", "Periodicity too low (score=$periodicityScore), skipping")
            return PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = ActionState.IDLE,
                count = count,
                confidence = 0f,
                debugInfo = "No periodicity (score=${String.format(Locale.getDefault(), "%.3f", periodicityScore)})",
                structuredDebugInfo = DetectionDebugInfo(
                    state = ActionState.IDLE, confidence = 0f,
                    message = "No periodicity (score=${String.format(Locale.getDefault(), "%.3f", periodicityScore)})"
                )
            )
        }

        // Compare sliding window with template using DTW
        val windowList = featureWindow.toList()
        val dtwDistance = computeDTW(windowList, templateFeatures)

        // Convert distance to similarity (normalize by sequence lengths)
        val maxPossibleDistance = windowSize * templateFeatures.size * kotlin.math.sqrt(9f)
        val normalizedDistance = dtwDistance / maxPossibleDistance.coerceAtLeast(1f)
        val similarity = 1f - normalizedDistance.coerceIn(0f, 1f)

        confidence = similarity
        lastSimilarity = similarity

        android.util.Log.d("CustomPoseActionDetector", "similarity=${String.format(Locale.getDefault(), "%.3f", similarity)}, movementScore=${String.format(Locale.getDefault(), "%.3f", movementScore)}, periodicityScore=${String.format(Locale.getDefault(), "%.3f", periodicityScore)}, threshold=$matchThreshold, templateSize=${templateFeatures.size}, windowSize=${windowList.size}")

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
                debugInfo = "Action detected! similarity=$similarity",
                structuredDebugInfo = DetectionDebugInfo(
                    state = currentState, confidence = similarity,
                    message = "Action detected! similarity=${String.format(Locale.getDefault(), "%.2f", similarity)}"
                )
            )
        } else {
            currentState = if (similarity >= matchThreshold * 0.7f) ActionState.IN_PROGRESS else ActionState.IDLE
            PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = currentState,
                count = count,
                confidence = similarity,
                debugInfo = "similarity=$similarity, threshold=$matchThreshold",
                structuredDebugInfo = DetectionDebugInfo(
                    state = currentState, confidence = similarity,
                    message = "similarity=${String.format(Locale.getDefault(), "%.2f", similarity)}, threshold=$matchThreshold"
                )
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

        // Hip center vertical position: helps distinguish squat (hip moves up/down)
        // from side-to-side movement (hip stays at similar vertical position).
        val hipCenterY = (leftHip[1] + rightHip[1]) / 2f

        // Normalize angles to [0, 1] range (divide by 180)
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
     * Compute movement score: average Euclidean distance from each frame
     * to the window's mean feature vector. This measures the overall spread
     * of the window and is more robust to frame-to-frame jitter than
     * consecutive-frame distance.
     *
     * Standing still with slight jitter: score ~0.03-0.05
     * Performing a movement: score ~0.15+
     */
    private fun computeMovementScore(window: ArrayDeque<FloatArray>): Float {
        if (window.size < 2) return 0f
        val list = window.toList()
        val dim = list[0].size

        // Compute mean for each dimension
        val mean = FloatArray(dim) { d ->
            list.sumOf { it[d].toDouble() }.toFloat() / list.size
        }

        // Compute average distance from each frame to the mean
        var totalDist = 0f
        for (frame in list) {
            var distSq = 0f
            for (d in 0 until dim) {
                val diff = frame[d] - mean[d]
                distSq += diff * diff
            }
            totalDist += kotlin.math.sqrt(distSq)
        }

        return totalDist / list.size
    }

    /**
     * Compute DTW distance between two feature sequences with Sakoe-Chiba constraint.
     * Each element is a FloatArray representing joint angles at one frame.
     * The constraint limits warping to prevent matching all frames to a single template frame.
     */
    private fun computeDTW(a: List<FloatArray>, b: List<FloatArray>): Float {
        val n = a.size
        val m = b.size
        if (n == 0 || m == 0) return Float.MAX_VALUE

        // Sakoe-Chiba band width: allow warping up to 25% of max sequence length
        val bandWidth = kotlin.math.max(n, m) / 4

        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0f

        for (i in 1..n) {
            // Determine valid j range for this i (Sakoe-Chiba constraint)
            val jMin = kotlin.math.max(1, (i * m / n) - bandWidth)
            val jMax = kotlin.math.min(m, (i * m / n) + bandWidth)
            for (j in jMin..jMax) {
                val cost = euclideanDistance(a[i - 1], b[j - 1])
                dtw[i][j] = cost + minOf(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
            }
        }

        return dtw[n][m]
    }

    private fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    private fun minOf(a: Float, b: Float, c: Float): Float {
        return kotlin.math.min(kotlin.math.min(a, b), c)
    }

    /**
     * Compute the dominant feature: the dimension with the largest variance
     * in the template. This is the feature that changes the most during the action.
     */
    private fun computeDominantFeature(features: List<FloatArray>): Int {
        if (features.isEmpty()) return 0
        val featureDim = features[0].size
        val variances = FloatArray(featureDim) { d ->
            val values = features.map { it[d] }
            val mean = values.sum() / values.size
            values.map { (it - mean) * (it - mean) }.sum() / values.size
        }
        return variances.withIndex().maxBy { it.value }.index
    }

    /**
     * Compute periodicity score of the dominant feature in the window.
     * This detects whether the feature shows a clear up-down pattern.
     * Score = (max - min) / (variance of the feature)
     * High score means clear periodic up-down movement.
     */
    private fun computePeriodicityScore(window: ArrayDeque<FloatArray>, dominantFeature: Int): Float {
        if (window.size < 4) return 0f
        val values = window.map { it[dominantFeature] }
        val mean = values.sum() / values.size
        val variance = values.map { (it - mean) * (it - mean) }.sum() / values.size
        val range = values.maxOrNull()!! - values.minOrNull()!!
        // Score: range / sqrt(variance + epsilon)
        // If variance is very small (static), score is low
        // If range is large relative to variance, score is high
        return range / kotlin.math.sqrt(variance + 1e-6f)
    }

    /**
     * Decode feature sequence from ByteArray.
     * Format: [4 bytes frame count][4 bytes feature dim][frame data...]
     */
    private fun decodeFeatureSequence(bytes: ByteArray?): List<FloatArray> {
        if (bytes == null || bytes.size < 8) return emptyList()

        val frameCount = readInt(bytes, 0)
        val featureDim = readInt(bytes, 4)

        val expectedSize = 8 + frameCount * featureDim * 4
        if (bytes.size < expectedSize || frameCount <= 0 || featureDim <= 0) {
            return emptyList()
        }

        val result = mutableListOf<FloatArray>()
        var offset = 8

        for (i in 0 until frameCount) {
            val frame = FloatArray(featureDim)
            for (j in 0 until featureDim) {
                frame[j] = readFloat(bytes, offset)
                offset += 4
            }
            result.add(frame)
        }

        // Backward compatibility: if template was recorded with 8 dimensions (old version),
        // pad with a 9th dimension (hip center Y = 0, neutral value)
        if (featureDim == 8) {
            return result.map { oldFrame ->
                FloatArray(9) { i -> if (i < 8) oldFrame[i] else 0f }
            }
        }

        return result
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readFloat(bytes: ByteArray, offset: Int): Float {
        val bits = readInt(bytes, offset)
        return Float.fromBits(bits)
    }
}
