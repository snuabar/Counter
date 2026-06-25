package com.snuabar.counter.core.detection.tflite.action

import com.snuabar.counter.domain.model.ActionType
import kotlin.math.abs

/**
 * Push-up detector.
 *
 * Detection logic (based on elbow angle):
 * - IDLE: waiting for arms to straighten (angle > 160°)
 * - IN_PROGRESS: arms bending (angle < 100°)
 * - COMPLETED: arms straighten again (angle > 160°) after bending
 *
 * Keypoint indices (COCO format):
 * - 5: left_shoulder, 6: right_shoulder
 * - 7: left_elbow, 8: right_elbow
 * - 9: left_wrist, 10: right_wrist
 */
class PushUpActionDetector : BasePoseActionDetector(ActionType.PUSH_UP) {

    // Thresholds for push-up detection
    private val straightArmThreshold = 130f  // Arms considered straight above this angle
    private val bentArmThreshold = 80f         // Arms considered bent below this threshold
    private val minConfidence = 0.1f

    override fun detect(keypoints: Array<FloatArray>): PoseActionResult? {
        updateCooldown()

        // Extract keypoints
        val leftShoulder = keypoints[5]
        val leftElbow = keypoints[7]
        val leftWrist = keypoints[9]
        val rightShoulder = keypoints[6]
        val rightElbow = keypoints[8]
        val rightWrist = keypoints[10]

        // Check visibility
        if (!isVisible(leftShoulder, minConfidence) || !isVisible(leftElbow, minConfidence) ||
            !isVisible(leftWrist, minConfidence) || !isVisible(rightShoulder, minConfidence) ||
            !isVisible(rightElbow, minConfidence) || !isVisible(rightWrist, minConfidence)
        ) {
            return null
        }

        // Calculate elbow angles (shoulder -> elbow -> wrist)
        val leftElbowAngle = calculateAngle(
            leftShoulder[0], leftShoulder[1],
            leftElbow[0], leftElbow[1],
            leftWrist[0], leftWrist[1]
        )
        val rightElbowAngle = calculateAngle(
            rightShoulder[0], rightShoulder[1],
            rightElbow[0], rightElbow[1],
            rightWrist[0], rightWrist[1]
        )

        // Use average of both elbows
        val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2f
        confidence = (leftElbow[2] + rightElbow[2]) / 2f

        return when (currentState) {
            ActionState.IDLE -> {
                // Waiting for arms to be straight (ready position)
                if (avgElbowAngle > straightArmThreshold) {
                    currentState = ActionState.IN_PROGRESS
                    PoseActionResult(actionType, false, currentState, count, confidence, "Arms straight - ready")
                } else {
                    PoseActionResult(actionType, false, currentState, count, confidence, "Waiting for straight arms")
                }
            }
            ActionState.IN_PROGRESS -> {
                // Arms bending down
                if (avgElbowAngle < bentArmThreshold) {
                    currentState = ActionState.COMPLETED
                    PoseActionResult(actionType, false, currentState, count, confidence, "Arms bent - push up detected")
                } else {
                    PoseActionResult(actionType, false, currentState, count, confidence, "Bending down...")
                }
            }
            ActionState.COMPLETED -> {
                // Arms pushing back up
                if (avgElbowAngle > straightArmThreshold) {
                    if (!isInCooldown()) {
                        count++
                        startCooldown()
                        currentState = ActionState.IDLE
                        PoseActionResult(actionType, true, currentState, count, confidence, "Push-up completed!")
                    } else {
                        PoseActionResult(actionType, false, currentState, count, confidence, "Cooldown...")
                    }
                } else {
                    PoseActionResult(actionType, false, currentState, count, confidence, "Pushing up...")
                }
            }
        }
    }
}
