package com.snuabar.counter.core.detection.tflite.action

import com.snuabar.counter.domain.model.ActionType
import com.snuabar.counter.domain.model.PoseDetector
import com.snuabar.counter.domain.model.PoseType
import com.snuabar.counter.domain.model.Template
import java.util.Locale
import kotlin.math.sqrt

/**
 * Body part categories for action classification.
 * Helps distinguish between leg-dominant (squat), arm-dominant (pull-up), etc.
 */
enum class BodyPart {
    ARMS,   // Arm-dominant: pull-ups, push-ups
    LEGS,   // Leg-dominant: squats, lunges
    HIP,    // Hip-dominant: hip thrusts, glute bridges
    MIXED,  // Mixed: burpees, mountain climbers
    UNKNOWN // Unknown or no clear dominant part
}

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
    // Increased from 15 to 25 to prevent double-counting during slow movements
    private val customCooldownFrames = 25
    private var lastSimilarity: Float = 0f

    // Minimum movement threshold: if feature window variance is below this,
    // the person is considered stationary and no counting occurs.
    // This is the average distance from each frame to the window mean.
    // Standing still with jitter: ~0.03-0.05; Actual movement: ~0.15+
    private val MIN_MOVEMENT_THRESHOLD = 0.08f

    // Dominant feature: the dimension with the largest variance in the template.
    // This is used for periodicity detection.
    private val dominantFeature: Int = computeDominantFeature(templateFeatures)

    // Dominant body part: which body part moves the most in the template.
    // This helps distinguish leg-dominant (squat) from arm-dominant (pull-up) actions.
    private val templateDominantPart: BodyPart = computeDominantBodyPart(templateFeatures)

    // Minimum periodicity score: the dominant feature must show clear up-down pattern
    // Need at least 1 extrema out of 4 max for clear periodicity
    private val MIN_PERIODICITY_SCORE = 0.25f

    override fun reset() {
        super.reset()
        featureWindow.clear()
    }

    override fun detect(keypoints: Array<FloatArray>): PoseActionResult? {
        updateCooldown()

        // Check pose compatibility first (if template has a known pose)
        val currentPose = PoseDetector.detectPose(keypoints)
        if (!PoseDetector.isPoseCompatible(template.poseType, currentPose)) {
            android.util.Log.d("CustomPoseActionDetector", "Pose mismatch: template=${template.poseType}, current=$currentPose, skipping")
            return PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = ActionState.IDLE,
                count = count,
                confidence = 0f,
                debugInfo = "Pose mismatch: expected ${template.poseType}, got $currentPose",
                structuredDebugInfo = DetectionDebugInfo(
                    state = ActionState.IDLE, confidence = 0f,
                    message = "Pose mismatch: expected ${template.poseType}, got $currentPose"
                )
            )
        }
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

        // Check dominant body part: the window must have the same dominant part as the template.
        // This prevents leg-dominant actions (squat) from matching arm-dominant actions (pull-up).
        val windowList = featureWindow.toList()
        val currentDominantPart = computeDominantBodyPart(windowList)
        if (!isBodyPartCompatible(templateDominantPart, currentDominantPart)) {
            android.util.Log.d("CustomPoseActionDetector", "Body part mismatch: template=$templateDominantPart, current=$currentDominantPart, skipping")
            return PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = ActionState.IDLE,
                count = count,
                confidence = 0f,
                debugInfo = "Body part mismatch: expected $templateDominantPart, got $currentDominantPart",
                structuredDebugInfo = DetectionDebugInfo(
                    state = ActionState.IDLE, confidence = 0f,
                    message = "Body part mismatch: expected $templateDominantPart, got $currentDominantPart"
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
        val dtwDistance = computeDTW(windowList, templateFeatures)

        // Compute template internal average frame distance for normalization
        val templateInternalDist = computeTemplateInternalDistance(templateFeatures)
        val windowInternalDist = computeTemplateInternalDistance(windowList)

        // Check if window has similar movement magnitude as template
        val movementRatio = windowInternalDist / (templateInternalDist + 1e-6f)
        if (movementRatio < 0.3f) {
            android.util.Log.d("CustomPoseActionDetector", "Movement ratio too low: movementRatio=$movementRatio, skipping")
            return PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = ActionState.IDLE,
                count = count,
                confidence = 0f,
                debugInfo = "Movement too small (ratio=${String.format(Locale.getDefault(), "%.3f", movementRatio)})",
                structuredDebugInfo = DetectionDebugInfo(
                    state = ActionState.IDLE, confidence = 0f,
                    message = "Movement too small (ratio=${String.format(Locale.getDefault(), "%.3f", movementRatio)})"
                )
            )
        }

        // Check hipCenterY range: for leg-dominant actions, significant hip movement is required.
        // For arm-dominant actions (e.g., clapping), hipCenterY may not change much, so skip this check.
        val templateHipYRange = templateFeatures.map { it[8] }.let { it.maxOrNull()!! - it.minOrNull()!! }
        val windowHipYRange = windowList.map { it[8] }.let { it.maxOrNull()!! - it.minOrNull()!! }
        val hipYRangeRatio = windowHipYRange / (templateHipYRange + 1e-6f)
        // Skip hipYRange check for arm-dominant actions (e.g., clapping, pull-ups)
        if (templateDominantPart != BodyPart.ARMS && hipYRangeRatio < 0.4f) {
            android.util.Log.d("CustomPoseActionDetector", "Hip Y range too small: hipYRangeRatio=$hipYRangeRatio, templateHipYRange=$templateHipYRange, windowHipYRange=$windowHipYRange, skipping")
            return PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = ActionState.IDLE,
                count = count,
                confidence = 0f,
                debugInfo = "Hip Y range too small (ratio=${String.format(Locale.getDefault(), "%.3f", hipYRangeRatio)})",
                structuredDebugInfo = DetectionDebugInfo(
                    state = ActionState.IDLE, confidence = 0f,
                    message = "Hip Y range too small (ratio=${String.format(Locale.getDefault(), "%.3f", hipYRangeRatio)})"
                )
            )
        }

        // Analyze knee angle range to help distinguish squat from jump
        // Squat: deep knee bend (large angle variation)
        // Jump: moderate knee bend, faster movement
        val templateKneeRange = computeKneeAngleRange(templateFeatures)
        val windowKneeRange = computeKneeAngleRange(windowList)
        val kneeRangeRatio = windowKneeRange / (templateKneeRange + 1e-6f)
        android.util.Log.d("CustomPoseActionDetector", "Knee angle ranges: template=$templateKneeRange, window=$windowKneeRange, ratio=$kneeRangeRatio")

        // Action signature validation (组合策略)
        // Validate extended features (indices 9-11) to distinguish squat from jump
        val templateSignature = computeActionSignature(templateFeatures)
        val windowSignature = computeActionSignature(windowList)
        val signatureMatch = validateActionSignature(templateSignature, windowSignature)
        android.util.Log.d("CustomPoseActionDetector", "Action signature match: $signatureMatch, template=$templateSignature, window=$windowSignature")

        // If signature doesn't match well, reduce similarity or reject
        if (signatureMatch < 0.5f) {
            android.util.Log.d("CustomPoseActionDetector", "Action signature mismatch: signatureMatch=$signatureMatch, skipping")
            return PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = ActionState.IDLE,
                count = count,
                confidence = 0f,
                debugInfo = "Action signature mismatch (match=${String.format(Locale.getDefault(), "%.3f", signatureMatch)})",
                structuredDebugInfo = DetectionDebugInfo(
                    state = ActionState.IDLE, confidence = 0f,
                    message = "Action signature mismatch (match=${String.format(Locale.getDefault(), "%.3f", signatureMatch)})"
                )
            )
        }

        // Normalize DTW distance using template internal distance as reference
        val pathLength = (windowList.size + templateFeatures.size) / 2f
        val avgDtwDist = dtwDistance / pathLength
        val ratio = avgDtwDist / (templateInternalDist + 1e-6f)
        val similarity = kotlin.math.exp(-ratio * 0.07f)

        confidence = similarity
        lastSimilarity = similarity

        android.util.Log.d("CustomPoseActionDetector", "similarity=${String.format(Locale.getDefault(), "%.3f", similarity)}, movementScore=${String.format(Locale.getDefault(), "%.3f", movementScore)}, periodicityScore=${String.format(Locale.getDefault(), "%.3f", periodicityScore)}, movementRatio=${String.format(Locale.getDefault(), "%.3f", movementRatio)}, threshold=$matchThreshold, templateSize=${templateFeatures.size}, windowSize=${windowList.size}")

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

        // Extended features for action differentiation (组合策略)
        // Feature 9: kneeAngleSum - sum of both knee angles, smaller when squatting deeper
        val kneeAngleSum = (leftKneeAngle + rightKneeAngle) / 360f
        // Feature 10: bodyHeight - vertical distance from shoulder center to ankle center
        // Smaller when squatting (body is lower)
        val shoulderCenterY = (leftShoulder[1] + rightShoulder[1]) / 2f
        val ankleCenterY = (keypoints[15][1] + keypoints[16][1]) / 2f
        val bodyHeight = shoulderCenterY - ankleCenterY
        // Feature 11: ankleY - average ankle vertical position
        // Used to detect "jumping off ground" vs "feet on ground"
        val ankleY = (keypoints[15][1] + keypoints[16][1]) / 2f

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
            hipCenterY,            // 9th dim: hip center vertical position [0,1]
            kneeAngleSum,          // 10th dim: knee angle sum [0,1], smaller when deeper squat
            bodyHeight,            // 11th dim: body height (shoulder to ankle)
            ankleY                 // 12th dim: average ankle Y position
        )
    }

    /**
     * Compute movement score: the maximum variance across any single feature dimension.
     * This ensures that even if only one body part moves (e.g., arms during clapping),
     * the movement score is still high enough to pass the threshold.
     *
     * Standing still with slight jitter: score ~0.03-0.05
     * Performing a movement: score ~0.15+
     */
    private fun computeMovementScore(window: ArrayDeque<FloatArray>): Float {
        if (window.size < 2) return 0f
        val list = window.toList()
        val dim = list[0].size

        var maxVariance = 0f
        for (d in 0 until dim) {
            val values = list.map { it[d] }
            val mean = values.sum() / values.size
            val variance = values.map { (it - mean) * (it - mean) }.sum() / values.size
            maxVariance = kotlin.math.max(maxVariance, variance)
        }

        return kotlin.math.sqrt(maxVariance)
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

    /**
     * Compute Euclidean distance between two feature vectors.
     * All dimensions are treated equally to enforce strict template matching.
     * This ensures that the detected action matches the recorded template exactly,
     * including hand/arm positions.
     */
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
     * Uses local extrema counting instead of range/variance ratio.
     */
    private fun computePeriodicityScore(window: ArrayDeque<FloatArray>, dominantFeature: Int): Float {
        if (window.size < 4) return 0f
        val values = window.map { it[dominantFeature] }

        // Count local extrema (peaks and valleys)
        var peakCount = 0
        for (i in 1 until values.size - 1) {
            if ((values[i] > values[i - 1] && values[i] > values[i + 1]) ||
                (values[i] < values[i - 1] && values[i] < values[i + 1])) {
                peakCount++
            }
        }

        // Normalize to [0, 1]: need at least 2 extrema for clear periodicity
        return kotlin.math.min(peakCount, 4) / 4.0f
    }

    /**
     * Compute average frame-to-frame distance within a feature sequence.
     * This represents the "internal movement" of the sequence and is used
     * as a reference for normalizing DTW distance.
     */
    private fun computeTemplateInternalDistance(features: List<FloatArray>): Float {
        if (features.size < 2) return 0f
        var totalDist = 0f
        for (i in 0 until features.size - 1) {
            totalDist += euclideanDistance(features[i], features[i + 1])
        }
        return totalDist / (features.size - 1)
    }

    /**
     * Action signature data class for storing key action characteristics.
     * Used to distinguish between similar actions (e.g., squat vs jump).
     */
    private data class ActionSignature(
        val kneeAngleSumMean: Float,  // Average knee angle sum (index 9)
        val kneeAngleSumRange: Float,  // Range of knee angle sum
        val bodyHeightMean: Float,    // Average body height (index 10)
        val bodyHeightRange: Float,    // Range of body height
        val ankleYMean: Float,        // Average ankle Y position (index 11)
        val ankleYRange: Float         // Range of ankle Y position
    ) {
        override fun toString(): String {
            return "kneeSum=%.3f/%.3f, bodyHeight=%.3f/%.3f, ankleY=%.3f/%.3f".format(
                Locale.getDefault(),
                kneeAngleSumMean, kneeAngleSumRange,
                bodyHeightMean, bodyHeightRange,
                ankleYMean, ankleYRange
            )
        }
    }

    /**
     * Compute action signature from a feature sequence.
     * Analyzes extended features (indices 9-11) to characterize the action.
     */
    private fun computeActionSignature(features: List<FloatArray>): ActionSignature {
        if (features.isEmpty()) return ActionSignature(0f, 0f, 0f, 0f, 0f, 0f)

        val kneeAngleSums = features.map { it[9] }
        val bodyHeights = features.map { it[10] }
        val ankleYs = features.map { it[11] }

        val kneeSumMean = kneeAngleSums.average().toFloat()
        val kneeSumRange = (kneeAngleSums.maxOrNull() ?: 0f) - (kneeAngleSums.minOrNull() ?: 0f)

        val bodyHeightMean = bodyHeights.average().toFloat()
        val bodyHeightRange = (bodyHeights.maxOrNull() ?: 0f) - (bodyHeights.minOrNull() ?: 0f)

        val ankleYMean = ankleYs.average().toFloat()
        val ankleYRange = (ankleYs.maxOrNull() ?: 0f) - (ankleYs.minOrNull() ?: 0f)

        return ActionSignature(
            kneeAngleSumMean = kneeSumMean,
            kneeAngleSumRange = kneeSumRange,
            bodyHeightMean = bodyHeightMean,
            bodyHeightRange = bodyHeightRange,
            ankleYMean = ankleYMean,
            ankleYRange = ankleYRange
        )
    }

    /**
     * Validate action signature match between template and current window.
     * Returns a score in [0, 1], where 1.0 means perfect match.
     * Uses extended features (indices 9-11) to distinguish squat from jump.
     */
    private fun validateActionSignature(template: ActionSignature, window: ActionSignature): Float {
        // Compare knee angle sum ranges (squat has larger range due to deep bending)
        val kneeSumRangeDiff = kotlin.math.abs(template.kneeAngleSumRange - window.kneeAngleSumRange)
        val kneeSumRangeScore = kotlin.math.exp(-kneeSumRangeDiff * 3.0f) // Higher penalty for large difference

        // Compare body height means (squat has lower average body height)
        val bodyHeightMeanDiff = kotlin.math.abs(template.bodyHeightMean - window.bodyHeightMean)
        val bodyHeightMeanScore = kotlin.math.exp(-bodyHeightMeanDiff * 2.0f)

        // Compare body height ranges (squat has larger range)
        val bodyHeightRangeDiff = kotlin.math.abs(template.bodyHeightRange - window.bodyHeightRange)
        val bodyHeightRangeScore = kotlin.math.exp(-bodyHeightRangeDiff * 3.0f)

        // Compare ankle Y ranges (jump has larger range due to leaving ground)
        val ankleYRangeDiff = kotlin.math.abs(template.ankleYRange - window.ankleYRange)
        val ankleYRangeScore = kotlin.math.exp(-ankleYRangeDiff * 2.0f)

        // Weighted combination: knee sum and body height are most important for squat vs jump
        return (kneeSumRangeScore * 0.35f +
                bodyHeightMeanScore * 0.25f +
                bodyHeightRangeScore * 0.25f +
                ankleYRangeScore * 0.15f)
    }

    /**
     * Compute the knee angle range (max - min) from a feature sequence.
     * Knee angles are at indices 6 (left) and 7 (right), normalized to [0, 1].
     * A larger range indicates deeper knee bending (e.g., squat vs jump).
     */
    private fun computeKneeAngleRange(features: List<FloatArray>): Float {
        if (features.isEmpty()) return 0f
        val leftKneeValues = features.map { it[6] }
        val rightKneeValues = features.map { it[7] }
        val leftRange = (leftKneeValues.maxOrNull() ?: 0f) - (leftKneeValues.minOrNull() ?: 0f)
        val rightRange = (rightKneeValues.maxOrNull() ?: 0f) - (rightKneeValues.minOrNull() ?: 0f)
        return (leftRange + rightRange) / 2f
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
        // pad with new dimensions (neutral values)
        if (featureDim == 8) {
            return result.map { oldFrame ->
                FloatArray(12) { i ->
                    when {
                        i < 8 -> oldFrame[i]
                        i == 8 -> 0f // hipCenterY
                        i == 9 -> oldFrame[6] + oldFrame[7] // kneeAngleSum approximation
                        i == 10 -> 0.5f // bodyHeight neutral
                        i == 11 -> 0.5f // ankleY neutral
                        else -> 0f
                    }
                }
            }
        }
        // Backward compatibility: if template was recorded with 9 dimensions (previous version)
        if (featureDim == 9) {
            return result.map { oldFrame ->
                FloatArray(12) { i ->
                    when {
                        i < 9 -> oldFrame[i]
                        i == 9 -> oldFrame[6] + oldFrame[7] // kneeAngleSum approximation
                        i == 10 -> 0.5f // bodyHeight neutral
                        i == 11 -> 0.5f // ankleY neutral
                        else -> 0f
                    }
                }
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

    /**
     * Compute the dominant body part from a feature sequence.
     * Analyzes which body part (arms, legs, hip) has the largest movement.
     * This helps distinguish leg-dominant (squat) from arm-dominant (pull-up) actions.
     *
     * Feature indices:
     * - Arms: 0-3 (elbow + shoulder angles)
     * - Legs: 6-7 (knee angles)
     * - Hip: 8 (hipCenterY)
     */
    private fun computeDominantBodyPart(features: List<FloatArray>): BodyPart {
        if (features.size < 4) return BodyPart.UNKNOWN

        // Compute variance for each body part group
        // Arms: indices 0,1,2,3 (elbow + shoulder angles)
        val armVariance = computeGroupVariance(features, listOf(0, 1, 2, 3))
        // Legs: indices 6,7 (knee angles)
        val legVariance = computeGroupVariance(features, listOf(6, 7))
        // Hip: index 8 (hipCenterY)
        val hipVariance = computeGroupVariance(features, listOf(8))

        android.util.Log.d("CustomPoseActionDetector", "Body part variances: arms=$armVariance, legs=$legVariance, hip=$hipVariance")

        // Find the dominant part
        val maxVariance = kotlin.math.max(armVariance, kotlin.math.max(legVariance, hipVariance))
        if (maxVariance < 0.001f) return BodyPart.UNKNOWN // Too little movement

        // Check if it's a mixed pattern (multiple parts move significantly)
        val threshold = maxVariance * 0.5f
        val significantParts = listOf(armVariance, legVariance, hipVariance).count { it >= threshold }

        return when {
            significantParts >= 2 -> BodyPart.MIXED
            maxVariance == armVariance -> BodyPart.ARMS
            maxVariance == legVariance -> BodyPart.LEGS
            maxVariance == hipVariance -> BodyPart.HIP
            else -> BodyPart.UNKNOWN
        }
    }

    /**
     * Compute the average variance of a group of feature dimensions.
     */
    private fun computeGroupVariance(features: List<FloatArray>, indices: List<Int>): Float {
        if (features.size < 2) return 0f
        return indices.map { dim ->
            val values = features.map { it[dim] }
            val mean = values.average().toFloat()
            values.map { (it - mean) * (it - mean) }.average().toFloat()
        }.average().toFloat()
    }

    /**
     * Check if two body parts are compatible for matching.
     * Mixed and unknown are compatible with everything.
     */
    private fun isBodyPartCompatible(templatePart: BodyPart, currentPart: BodyPart): Boolean {
        if (templatePart == BodyPart.UNKNOWN || currentPart == BodyPart.UNKNOWN) return true
        if (templatePart == BodyPart.MIXED || currentPart == BodyPart.MIXED) return true
        return templatePart == currentPart
    }

    private fun readFloat(bytes: ByteArray, offset: Int): Float {
        val bits = readInt(bytes, offset)
        return Float.fromBits(bits)
    }
}
