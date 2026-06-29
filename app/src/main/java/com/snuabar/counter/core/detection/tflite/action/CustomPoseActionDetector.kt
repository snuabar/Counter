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
    private val template: Template,
    private val config: DetectionConfig = DetectionConfig()
) : BasePoseActionDetector(ActionType.CUSTOM) {

    // Sliding window of recent feature vectors
    private val featureWindow = ArrayDeque<FloatArray>()

    // Template feature sequence (decoded from bytes)
    private val templateFeatures: List<FloatArray> = decodeFeatureSequence(template.featureVector)
    // Template velocity features (A: 速度/加速度特征)
    private val templateVelFeatures: List<FloatArray> = if (config.useVelocityFeatures) {
        computeVelocityFeatures(templateFeatures)
    } else {
        emptyList()
    }
    // Fixed threshold for all templates
    private val matchThreshold: Float = 0.65f

    // Window size matches template length for DTW comparison
    private val windowSize = templateFeatures.size.coerceIn(8, 60)

    // Cooldown frames to prevent double-counting
    // Computed adaptively based on template periodicity in init
    private var customCooldownFrames = 20
    private var lastSimilarity: Float = 0f

    // TODO-A: 速度/加速度特征已实现
    // When config.useVelocityFeatures is enabled, both position and velocity DTW distances
    // are computed and combined with weights (70% position + 30% velocity).

    // Rising-edge detection: only trigger when similarity crosses threshold from below
    private var prevSimilarity: Float = 0f
    private var wasDetected: Boolean = false

    // Peak tracking: reset wasDetected when similarity drops 30% from peak after detection
    private var peakSimilarity: Float = 0f

    // Lock: after triggering, must wait for similarity to drop below threshold before re-triggering.
    // This prevents duplicate counts during the same slow motion (long similarity plateau).
    private var isLocked: Boolean = false

    // Minimum movement threshold
    private val MIN_MOVEMENT_THRESHOLD = 0.01f

    // Dominant feature: the dimension with the largest variance in the template.
    private val dominantFeature: Int = computeDominantFeature(templateFeatures)

    // Dominant body part
    private val templateDominantPart: BodyPart = computeDominantBodyPart(templateFeatures)

    // Template feature ranges for amplitude consistency check
    private var templateRanges: FloatArray = FloatArray(0)
    private var top3Dims: IntArray = IntArray(0)

    // Feature dimension adaptive weights (B: 特征维度自适应加权)
    private val featureWeights: FloatArray = if (config.useAdaptiveWeights) {
        computeFeatureWeights(templateFeatures)
    } else {
        FloatArray(0)
    }
    // Velocity feature adaptive weights (B+A: 速度特征维度自适应加权)
    private val velFeatureWeights: FloatArray = if (config.useAdaptiveWeights && config.useVelocityFeatures && templateVelFeatures.isNotEmpty()) {
        computeFeatureWeights(templateVelFeatures)
    } else {
        FloatArray(0)
    }

    // Adaptive smoothing window size (E: 自适应平滑)
    // Computed from template characteristics: fast actions get minimal smoothing (1),
    // medium actions get default (3), slow actions get stronger smoothing (5).
    private val adaptiveSmoothingWindowSize: Int = computeAdaptiveSmoothingWindowSize(templateFeatures)

    // Online learning: updated template features (F: 模板在线学习)
    // When enabled, the template is gradually adapted to the user's execution style.
    private var onlineTemplateFeatures: List<FloatArray>? = null
    private var onlineTemplateCount: Int = 0

    init {
        android.util.Log.d("CustomPoseActionDetector", "Loaded template: name=${template.name}, featureVectorSize=${template.featureVector?.size ?: 0}, decodedFrames=${templateFeatures.size}, windowSize=$windowSize")
        if (templateFeatures.isEmpty()) {
            android.util.Log.e("CustomPoseActionDetector", "WARNING: Template has no feature vectors! featureVector=${template.featureVector?.size ?: 0} bytes")
        }

        // Compute template feature ranges for amplitude consistency check
        templateRanges = computeFeatureRanges(templateFeatures)
        top3Dims = findTopNDims(templateRanges, n = 3)

        // Compute adaptive cooldown based on template periodicity
        // Fast actions (clapping): fewer frames per period → shorter cooldown
        // Slow actions (squat): more frames per period → longer cooldown
        customCooldownFrames = computeAdaptiveCooldown(templateFeatures)
        android.util.Log.d("CustomPoseActionDetector", "Template ranges: ${templateRanges.joinToString { String.format("%.3f", it) }}, top3Dims=${top3Dims.contentToString()}, adaptiveCooldown=$customCooldownFrames")
    }

    // Minimum periodicity score: the dominant feature must show clear up-down pattern
    // Need at least 1 extrema out of 4 max for clear periodicity
    private val MIN_PERIODICITY_SCORE = 0.25f

    override fun reset() {
        super.reset()
        featureWindow.clear()
        prevSimilarity = 0f
        wasDetected = false
        isLocked = false
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

        // TODO-E: 自适应平滑预留点
        // When config.useAdaptiveSmoothing is enabled, the currentFeatures should be
        // smoothed using an adaptive window size based on the template's peakMotion:
        // Fast actions (peakMotion > 1.0): window=1-2 (minimal smoothing to preserve sharp peaks)
        // Medium actions: window=3 (default)
        // Slow actions (peakMotion < 0.3): window=5 (stronger smoothing to reduce jitter)
        // The smoothed features then replace currentFeatures for the sliding window.

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
            // Reset detection state when user is stationary to allow re-triggering on next action
            wasDetected = false
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

        // Check body part compatibility to prevent mismatched actions (e.g., squat template matched by arm waving)
        // Only block clear opposites: LEGS vs ARMS. Allow MIXED/UNKNOWN to pass.
        val windowList = if (config.useAdaptiveSmoothing && adaptiveSmoothingWindowSize > 1) {
            smoothFeatures(featureWindow.toList(), adaptiveSmoothingWindowSize)
        } else {
            featureWindow.toList()
        }
        val currentDominantPart = computeDominantBodyPart(windowList)
        if (isBodyPartOpposite(templateDominantPart, currentDominantPart)) {
            android.util.Log.d("CustomPoseActionDetector", "Body part opposite: template=$templateDominantPart, current=$currentDominantPart, skipping")
            return PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = ActionState.IDLE,
                count = count,
                confidence = 0f,
                debugInfo = "Body part opposite: expected $templateDominantPart, got $currentDominantPart",
                structuredDebugInfo = DetectionDebugInfo(
                    state = ActionState.IDLE, confidence = 0f,
                    message = "Body part opposite: expected $templateDominantPart, got $currentDominantPart"
                )
            )
        }

        // Compare sliding window with template using DTW
        val dtwPosDistance = if (config.useOnlineLearning && onlineTemplateFeatures != null) {
            // Compare with both original and online templates, take the minimum distance (F: 模板在线学习)
            val originalDistance = computeDTW(windowList, templateFeatures)
            val onlineDistance = computeDTW(windowList, onlineTemplateFeatures!!)
            kotlin.math.min(originalDistance, onlineDistance)
        } else {
            computeDTW(windowList, templateFeatures)
        }

        // Compute velocity DTW distance if enabled (A: 速度/加速度特征)
        val dtwVelDistance = if (config.useVelocityFeatures && templateVelFeatures.isNotEmpty()) {
            val windowVelFeatures = computeVelocityFeatures(windowList)
            if (windowVelFeatures.isNotEmpty()) {
                computeDTW(windowVelFeatures, templateVelFeatures, velFeatureWeights)
            } else {
                0f
            }
        } else {
            0f
        }

        // Normalize DTW distance by path length for fair comparison
        val pathLength = (windowList.size + templateFeatures.size) / 2f
        val avgDtwPosDist = dtwPosDistance / pathLength

        // Combine position and velocity distances (A: 速度/加速度特征)
        val avgDtwDist = if (config.useVelocityFeatures && templateVelFeatures.isNotEmpty()) {
            val velPathLength = (windowList.size + templateVelFeatures.size) / 2f
            val avgDtwVelDist = dtwVelDistance / velPathLength
            0.7f * avgDtwPosDist + 0.3f * avgDtwVelDist
        } else {
            avgDtwPosDist
        }

        // Velocity score: how well the current window's speed pattern matches the template.
        // 0 = very different speed (too fast or too slow); 1 = perfectly matched speed.
        val velocityScore = if (config.useVelocityFeatures && templateVelFeatures.isNotEmpty()) {
            val velPathLength = (windowList.size + templateVelFeatures.size) / 2f
            val avgDtwVelDist = dtwVelDistance / velPathLength
            kotlin.math.exp(-avgDtwVelDist * 0.5f).coerceIn(0f, 1f)
        } else {
            // Fallback: derive a coarse speed match from position similarity
            kotlin.math.exp(-avgDtwPosDist * 0.3f).coerceIn(0f, 1f)
        }

        // Direct similarity: no templateInternalDist normalization
        // This prevents templates with small internal motion from being matched too easily
        val similarity = kotlin.math.exp(-avgDtwDist * 0.5f)

        confidence = similarity
        lastSimilarity = similarity

        android.util.Log.d("CustomPoseActionDetector", "similarity=${String.format(Locale.getDefault(), "%.3f", similarity)}, movementScore=${String.format(Locale.getDefault(), "%.3f", movementScore)}, threshold=$matchThreshold, templateSize=${templateFeatures.size}, windowSize=${windowList.size}, cooldown=${isInCooldown()}")

        // Check amplitude consistency: current window must have similar movement magnitude as template
        // This prevents small movements (e.g., standing sway) from matching large-movement templates
        val windowRanges = computeFeatureRanges(windowList)
        var amplitudeMatchScore = 0f
        var checkedDims = 0
        for (dim in top3Dims) {
            if (templateRanges[dim] > 0.01f) { // Only check dimensions with significant change in template
                val ratio = kotlin.math.min(windowRanges[dim] / templateRanges[dim], 2f) // Cap at 2x to avoid penalizing exaggerated movements
                amplitudeMatchScore += if (ratio >= 0.6f) 1f else ratio // Partial credit
                checkedDims++
            }
        }
        val amplitudeRatio = if (checkedDims > 0) amplitudeMatchScore / checkedDims else 1f

        android.util.Log.d("CustomPoseActionDetector", "amplitudeRatio=${String.format(Locale.getDefault(), "%.2f", amplitudeRatio)}, templateRanges=${top3Dims.map { templateRanges[it] }.joinToString { String.format("%.3f", it) }}, windowRanges=${top3Dims.map { windowRanges[it] }.joinToString { String.format("%.3f", it) }}")

        // Peak tracking: track the highest similarity after detection
        if (wasDetected && similarity > peakSimilarity) {
            peakSimilarity = similarity
        }

        // Rising-edge detection: only trigger when similarity crosses threshold from below.
        // Peak-based hysteresis: after detection, reset when similarity drops 30% from peak.
        // Lock-based protection: after triggering, must wait for similarity to drop below threshold
        // before re-triggering. This prevents duplicate counts during the same slow motion.
        var isDetected = false
        if (similarity >= matchThreshold && amplitudeRatio >= 0.6f && !wasDetected && !isInCooldown() && !isLocked) {
            count++
            cooldownCounter = customCooldownFrames
            currentState = ActionState.COMPLETED
            wasDetected = true
            isLocked = true  // Lock until similarity drops below threshold
            peakSimilarity = similarity
            isDetected = true
            // F: 模板在线学习
            if (config.useOnlineLearning) {
                updateTemplateOnline(windowList)
            }
            android.util.Log.i("CustomPoseActionDetector", "ACTION DETECTED! count=$count, similarity=$similarity")
        } else if (wasDetected && peakSimilarity > 0 && similarity < peakSimilarity * 0.7f) {
            // Similarity dropped 30% from peak → action is over, allow re-triggering
            wasDetected = false
            peakSimilarity = 0f
            currentState = ActionState.IDLE
        } else if (similarity < matchThreshold * 0.3f) {
            wasDetected = false
            peakSimilarity = 0f
            currentState = if (similarity >= matchThreshold * 0.7f) ActionState.IN_PROGRESS else ActionState.IDLE
        } else {
            currentState = if (similarity >= matchThreshold * 0.7f) ActionState.IN_PROGRESS else ActionState.IDLE
        }

        // Unlock: only when similarity drops below threshold can we re-trigger.
        // This prevents duplicate counts during long similarity plateaus (slow motions).
        if (isLocked && similarity < matchThreshold) {
            isLocked = false
        }

        // Update prevSimilarity for next frame
        prevSimilarity = similarity

        return if (isDetected) {
            PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = true,
                currentState = currentState,
                count = count,
                confidence = similarity,
                velocityScore = velocityScore,
                debugInfo = "Action detected! similarity=$similarity, velocityScore=$velocityScore",
                structuredDebugInfo = DetectionDebugInfo(
                    state = currentState, confidence = similarity,
                    message = "Action detected! similarity=${String.format(Locale.getDefault(), "%.2f", similarity)}"
                )
            )
        } else {
            PoseActionResult(
                actionType = ActionType.CUSTOM,
                isDetected = false,
                currentState = currentState,
                count = count,
                confidence = similarity,
                velocityScore = velocityScore,
                debugInfo = "similarity=$similarity, threshold=$matchThreshold, velocityScore=$velocityScore",
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

        // Check visibility of all required keypoints including wrist and ankle
        val leftWrist = keypoints[9]
        val rightWrist = keypoints[10]
        val leftAnkle = keypoints[15]
        val rightAnkle = keypoints[16]
        val required = arrayOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftHip, rightHip, leftKnee, rightKnee, leftWrist, rightWrist, leftAnkle, rightAnkle)
        if (required.any { !isVisible(it, minConfidence) }) return null

        // Compute joint angles (in degrees, then normalize to [0,1])
        val leftElbowAngle = calculateAngle(
            leftShoulder[0], leftShoulder[1],
            leftElbow[0], leftElbow[1],
            leftWrist[0], leftWrist[1] // left_wrist
        )
        val rightElbowAngle = calculateAngle(
            rightShoulder[0], rightShoulder[1],
            rightElbow[0], rightElbow[1],
            rightWrist[0], rightWrist[1] // right_wrist
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
            leftAnkle[0], leftAnkle[1] // left_ankle
        )
        val rightKneeAngle = calculateAngle(
            rightHip[0], rightHip[1],
            rightKnee[0], rightKnee[1],
            rightAnkle[0], rightAnkle[1] // right_ankle
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
        val ankleCenterY = (leftAnkle[1] + rightAnkle[1]) / 2f
        val bodyHeight = shoulderCenterY - ankleCenterY
        // Feature 11: ankleY - average ankle vertical position
        // Used to detect "jumping off ground" vs "feet on ground"
        val ankleY = (leftAnkle[1] + rightAnkle[1]) / 2f

        // Feature 12: hand distance ratio - distinguishes clapping (hands together) from waving (hands apart)
        // Uses index finger tips (coco17[3] and coco17[4]) for best hand contact detection
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

        // D: Height/distance normalization
        // Normalize bodyHeight by shoulderWidth to eliminate distance effects.
        // When enabled, bodyHeight becomes a body-proportion invariant (body height / shoulder width).
        val normalizedBodyHeight = if (config.useNormalization && shoulderWidth > 0.01f) {
            bodyHeight / shoulderWidth
        } else {
            bodyHeight
        }

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
            normalizedBodyHeight,  // 11th dim: body height (shoulder to ankle), normalized by shoulder width when enabled
            ankleY,                // 12th dim: average ankle Y position
            handDistanceRatio      // 13th dim: hand distance / shoulder width (0=clapping, 1=waving)
        )
    }

    /**
     * Compute movement score: group by body part and use the dominant group's variance.
     * This prevents arm-dominant actions (clapping) from being filtered out by leg variance.
     * Also prevents stationary jitter from triggering (all groups have low variance).
     */
    private fun computeMovementScore(window: ArrayDeque<FloatArray>): Float {
        if (window.size < 2) return 0f
        val list = window.toList()

        // Group variances by body part
        // Arms: indices 0-3 (elbow + shoulder angles) + 12 (handDistanceRatio)
        // Legs: indices 6-7 (knee angles)
        // Hip: index 8 (hipCenterY)
        val armVariance = computeGroupVariance(list, listOf(0, 1, 2, 3, 12))
        val legVariance = computeGroupVariance(list, listOf(6, 7))
        val hipVariance = computeGroupVariance(list, listOf(8))

        // If arms move significantly more than legs/hip → arm-dominant action
        // If all parts move similarly and little → stationary jitter
        return if (armVariance > legVariance * 2f && armVariance > hipVariance * 2f) {
            // Arm-dominant: use arm variance (higher sensitivity for clapping, etc.)
            kotlin.math.sqrt(armVariance)
        } else if (legVariance > armVariance * 2f && legVariance > hipVariance * 2f) {
            // Leg-dominant: use leg variance
            kotlin.math.sqrt(legVariance)
        } else {
            // Mixed or stationary: use overall max variance (more strict)
            kotlin.math.sqrt(kotlin.math.max(armVariance, kotlin.math.max(legVariance, hipVariance)))
        }
    }

    /**
     * Compute DTW distance between two feature sequences with Sakoe-Chiba constraint.
     * Each element is a FloatArray representing joint angles at one frame.
     * The constraint limits warping to prevent matching all frames to a single template frame.
     *
     * When useAdaptiveWeights is enabled, uses weighted Euclidean distance based on
     * template feature variances to emphasize dimensions with the most motion.
     */
    private fun computeDTW(a: List<FloatArray>, b: List<FloatArray>, weights: FloatArray = featureWeights): Float {
        val n = a.size
        val m = b.size
        if (n == 0 || m == 0) return Float.MAX_VALUE

        // Sakoe-Chiba band width: allow warping up to 50% of max sequence length
        // Increased from 25% to better handle speed variations in clapping/squatting
        val bandWidth = kotlin.math.max(n, m) / 2

        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0f

        for (i in 1..n) {
            // Determine valid j range for this i (Sakoe-Chiba constraint)
            val jMin = kotlin.math.max(1, (i * m / n) - bandWidth)
            val jMax = kotlin.math.min(m, (i * m / n) + bandWidth)
            for (j in jMin..jMax) {
                val cost = if (config.useAdaptiveWeights && weights.isNotEmpty()) {
                    weightedEuclideanDistance(a[i - 1], b[j - 1], weights)
                } else {
                    euclideanDistance(a[i - 1], b[j - 1])
                }
                dtw[i][j] = cost + minOf(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
            }
        }

        return dtw[n][m]
    }

    /**
     * Compute Euclidean distance between two feature vectors.
     * All dimensions are treated equally.
     */
    private fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Compute weighted Euclidean distance between two feature vectors.
     * Dimensions with higher weights contribute more to the distance.
     * Used when useAdaptiveWeights is enabled to emphasize dominant motion dimensions.
     */
    private fun weightedEuclideanDistance(a: FloatArray, b: FloatArray, weights: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += weights[i] * diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Compute adaptive weights for each feature dimension based on template variance.
     * Dimensions with larger variance (more motion) get higher weights.
     * Weights are normalized so their sum equals the number of dimensions.
     */
    private fun computeFeatureWeights(features: List<FloatArray>): FloatArray {
        if (features.isEmpty() || features[0].isEmpty()) return FloatArray(0)
        val dim = features[0].size
        val variances = FloatArray(dim) { d ->
            val values = features.map { it[d] }
            val mean = values.sum() / values.size
            values.map { (it - mean) * (it - mean) }.sum() / values.size
        }
        // Normalize: variance-based weights sum to dim (average weight = 1.0)
        val totalVariance = variances.sum()
        return if (totalVariance > 0) {
            FloatArray(dim) { i -> (variances[i] / totalVariance) * dim }
        } else {
            FloatArray(dim) { 1f }
        }
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

        // Backward compatibility: pad old templates to current 13 dimensions
        // Current: 13 dims (0-7: angles, 8: hipCenterY, 9: kneeAngleSum, 10: bodyHeight, 11: ankleY, 12: handDistanceRatio)
        when (featureDim) {
            8 -> return result.map { oldFrame ->
                FloatArray(13) { i ->
                    when {
                        i < 8 -> oldFrame[i]
                        i == 8 -> 0f // hipCenterY
                        i == 9 -> oldFrame[6] + oldFrame[7] // kneeAngleSum approximation
                        i == 10 -> 0.5f // bodyHeight neutral
                        i == 11 -> 0.5f // ankleY neutral
                        i == 12 -> 0.5f // handDistanceRatio neutral
                        else -> 0f
                    }
                }
            }
            9 -> return result.map { oldFrame ->
                FloatArray(13) { i ->
                    when {
                        i < 9 -> oldFrame[i]
                        i == 9 -> oldFrame[6] + oldFrame[7] // kneeAngleSum approximation
                        i == 10 -> 0.5f // bodyHeight neutral
                        i == 11 -> 0.5f // ankleY neutral
                        i == 12 -> 0.5f // handDistanceRatio neutral
                        else -> 0f
                    }
                }
            }
            12 -> return result.map { oldFrame ->
                FloatArray(13) { i ->
                    when {
                        i < 12 -> oldFrame[i]
                        i == 12 -> 0.5f // handDistanceRatio neutral (not available in old templates)
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
     * - Arms: 0-3 (elbow + shoulder angles) + 12 (handDistanceRatio)
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

    /**
     * Check if two body parts are clear opposites (should block).
     * Only blocks LEGS vs ARMS to prevent squat template being matched by arm waving.
     * HIP is not blocked as it's less common to confuse.
     */
    private fun isBodyPartOpposite(templatePart: BodyPart, currentPart: BodyPart): Boolean {
        // Only block clear opposites
        return (templatePart == BodyPart.LEGS && currentPart == BodyPart.ARMS) ||
               (templatePart == BodyPart.ARMS && currentPart == BodyPart.LEGS)
    }

    /**
     * Compute feature ranges (max - min) for each dimension in a feature sequence.
     */
    private fun computeFeatureRanges(features: List<FloatArray>): FloatArray {
        if (features.isEmpty()) return FloatArray(0)
        val dim = features[0].size
        return FloatArray(dim) { d ->
            val values = features.map { it[d] }
            (values.maxOrNull() ?: 0f) - (values.minOrNull() ?: 0f)
        }
    }

    /**
     * Find top N dimensions with largest ranges.
     */
    private fun findTopNDims(ranges: FloatArray, n: Int): IntArray {
        return ranges.withIndex()
            .sortedByDescending { it.value }
            .take(n)
            .map { it.index }
            .toIntArray()
    }

    /**
     * Compute adaptive cooldown based on template periodicity.
     * Analyzes the dominant feature dimension to estimate action period,
     * then sets cooldown to ~70% of one period.
     * Fast actions (clapping): shorter cooldown; Slow actions (squat): longer cooldown.
     */
    private fun computeAdaptiveCooldown(features: List<FloatArray>): Int {
        if (features.size < 8) return 10 // Fallback for very short templates

        // Find dominant feature (largest variance)
        val variances = FloatArray(features[0].size) { d ->
            val values = features.map { it[d] }
            val mean = values.average().toFloat()
            values.map { (it - mean) * (it - mean) }.average().toFloat()
        }
        val dominantDim = variances.withIndex().maxBy { it.value }.index

        // Count peaks in dominant dimension
        val values = features.map { it[dominantDim] }
        var peakCount = 0
        for (i in 1 until values.size - 1) {
            if (values[i] > values[i - 1] && values[i] > values[i + 1]) {
                peakCount++
            }
        }

        // If no clear peaks, assume 1 full cycle in template
        if (peakCount == 0) peakCount = 1

        // Estimate frames per period
        val framesPerPeriod = features.size.toFloat() / peakCount

        // Cooldown = 50% of period for fast actions, 70% for slower ones.
        // Lower bound of 5 frames (~0.17s) allows very rapid re-triggering (e.g., fast clapping).
        val ratio = if (framesPerPeriod < 20) 0.5f else 0.7f
        val cooldown = kotlin.math.max(5, kotlin.math.min(25, (framesPerPeriod * ratio).toInt()))

        android.util.Log.d("CustomPoseActionDetector", "Adaptive cooldown: dominantDim=$dominantDim, peakCount=$peakCount, framesPerPeriod=${String.format("%.1f", framesPerPeriod)}, ratio=$ratio, cooldown=$cooldown")

        return cooldown
    }

    private fun readFloat(bytes: ByteArray, offset: Int): Float {
        val bits = readInt(bytes, offset)
        return Float.fromBits(bits)
    }

    // ===== E: Adaptive Smoothing =====

    /**
     * Compute adaptive smoothing window size based on template characteristics.
     * Fast actions (few frames, high motion): minimal smoothing (1) to preserve sharp peaks.
     * Medium actions: default smoothing (3).
     * Slow actions (many frames, low motion): stronger smoothing (5) to reduce jitter.
     */
    private fun computeAdaptiveSmoothingWindowSize(features: List<FloatArray>): Int {
        if (features.size < 15) return 1 // Fast action: no smoothing
        if (features.size > 40) return 5 // Slow action: stronger smoothing
        return 3 // Medium action: default smoothing
    }

    /**
     * Smooth a sequence of feature vectors using sliding average.
     * Each dimension is smoothed independently with a symmetric window.
     * Edge frames use replicate padding (copy nearest valid value).
     * @param windowSize must be odd (1, 3, 5...). Window size of 1 returns the original sequence.
     */
    private fun smoothFeatures(features: List<FloatArray>, windowSize: Int): List<FloatArray> {
        if (windowSize <= 1 || features.size < windowSize) return features
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

    /**
     * Compute velocity features from a sequence of position features.
     * Velocity at frame i = position[i+1] - position[i] (forward difference).
     * The last frame's velocity is copied from the previous frame.
     * Used when useVelocityFeatures is enabled to incorporate motion dynamics into DTW matching.
     */
    private fun computeVelocityFeatures(features: List<FloatArray>): List<FloatArray> {
        if (features.size < 2) return emptyList()
        val result = mutableListOf<FloatArray>()
        for (i in features.indices) {
            val vel = if (i < features.size - 1) {
                FloatArray(features[0].size) { d -> features[i + 1][d] - features[i][d] }
            } else {
                // Last frame: copy previous velocity
                FloatArray(features[0].size) { d -> features[i][d] - features[i - 1][d] }
            }
            result.add(vel)
        }
        return result
    }

    // ===== F: Online Learning =====

    /**
     * Update the online template with the current detected window.
     * Uses a small learning rate (alpha) to gradually adapt the template to the user's style.
     * The first detection initializes the online template; subsequent detections apply
     * incremental updates to overlapping frames.
     */
    private fun updateTemplateOnline(windowList: List<FloatArray>) {
        if (!config.useOnlineLearning) return
        if (windowList.isEmpty()) return

        if (onlineTemplateFeatures == null) {
            // First detection: initialize online template with the current window
            onlineTemplateFeatures = windowList.map { it.copyOf() }
            onlineTemplateCount = 1
            android.util.Log.i("CustomPoseActionDetector", "Online learning: initialized template with ${windowList.size} frames")
        } else {
            // Incremental update: blend current window into existing template
            val alpha = 0.1f // Learning rate
            val template = onlineTemplateFeatures!!
            val minLength = kotlin.math.min(template.size, windowList.size)
            if (minLength == 0) return
            val featureDim = template[0].size

            for (i in 0 until minLength) {
                for (d in 0 until featureDim) {
                    template[i][d] = (1 - alpha) * template[i][d] + alpha * windowList[i][d]
                }
            }
            onlineTemplateCount++
            android.util.Log.i("CustomPoseActionDetector", "Online learning: updated template (count=$onlineTemplateCount, alpha=$alpha)")
        }
    }
}
