package com.snuabar.counter.core.detection.tflite.action

import kotlin.math.abs

/**
 * Plank detector (static pose hold).
 *
 * Detection logic:
 * - Detects if body is in plank position (straight line from shoulders to ankles)
 * - Counts time while maintaining plank position
 * - IDLE: not in plank position
 * - IN_PROGRESS: holding plank position
 * - COMPLETED: user finishes plank (optional: timer mode)
 *
 * Keypoint indices (COCO format):
 * - 5: left_shoulder, 6: right_shoulder
 * - 11: left_hip, 12: right_hip
 * - 15: left_ankle, 16: right_ankle
 */
class PlankActionDetector : BasePoseActionDetector(ActionType.PLANK) {

    // Thresholds for plank detection
    private val straightBodyThreshold = 15f   // Max deviation from straight line (degrees)
    private val minConfidence = 0.5f
    private var frameCount = 0
    private val requiredFrames = 30             // Must hold for ~1 second (at 30fps) to count

    override fun detect(keypoints: Array<FloatArray>): PoseActionResult? {
        updateCooldown()

        // Extract keypoints
        val leftShoulder = keypoints[5]
        val rightShoulder = keypoints[6]
        val leftHip = keypoints[11]
        val rightHip = keypoints[12]
        val leftAnkle = keypoints[15]
        val rightAnkle = keypoints[16]

        // Check visibility
        if (!isVisible(leftShoulder, minConfidence) || !isVisible(rightShoulder, minConfidence) ||
            !isVisible(leftHip, minConfidence) || !isVisible(rightHip, minConfidence) ||
            !isVisible(leftAnkle, minConfidence) || !isVisible(rightAnkle, minConfidence)
        ) {
            return null
        }

        // Calculate body straightness using shoulder-hip-ankle alignment
        val leftBodyAngle = calculateAngle(
            leftShoulder[0], leftShoulder[1],
            leftHip[0], leftHip[1],
            leftAnkle[0], leftAnkle[1]
        )
        val rightBodyAngle = calculateAngle(
            rightShoulder[0], rightShoulder[1],
            rightHip[0], rightHip[1],
            rightAnkle[0], rightAnkle[1]
        )

        // For plank, the body should be close to a straight line
        // The angle should be close to 180°
        val leftDeviation = abs(180f - leftBodyAngle)
        val rightDeviation = abs(180f - rightBodyAngle)
        val avgDeviation = (leftDeviation + rightDeviation) / 2f

        confidence = (leftHip[2] + rightHip[2]) / 2f

        return when (currentState) {
            ActionState.IDLE -> {
                // Waiting for plank position
                if (avgDeviation < straightBodyThreshold) {
                    frameCount++
                    if (frameCount >= requiredFrames) {
                        currentState = ActionState.IN_PROGRESS
                        frameCount = 0
                        PoseActionResult(actionType, false, currentState, count, confidence, "Plank position detected - holding")
                    } else {
                        PoseActionResult(actionType, false, currentState, count, confidence, "Detecting plank... ($frameCount/$requiredFrames)")
                    }
                } else {
                    frameCount = 0
                    PoseActionResult(actionType, false, currentState, count, confidence, "Get into plank position")
                }
            }
            ActionState.IN_PROGRESS -> {
                // Holding plank
                if (avgDeviation < straightBodyThreshold) {
                    frameCount++
                    // Count every 30 frames (~1 second)
                    if (frameCount >= requiredFrames) {
                        count++
                        frameCount = 0
                        PoseActionResult(actionType, true, currentState, count, confidence, "Plank held for 1 second - count: $count")
                    } else {
                        PoseActionResult(actionType, false, currentState, count, confidence, "Holding plank... ($frameCount/$requiredFrames)")
                    }
                } else {
                    // Lost plank position
                    currentState = ActionState.IDLE
                    frameCount = 0
                    PoseActionResult(actionType, false, currentState, count, confidence, "Lost plank position")
                }
            }
            ActionState.COMPLETED -> {
                // For plank, COMPLETED means user finished holding
                currentState = ActionState.IDLE
                frameCount = 0
                PoseActionResult(actionType, false, currentState, count, confidence, "Plank completed")
            }
        }
    }

    override fun reset() {
        super.reset()
        frameCount = 0
    }
}
