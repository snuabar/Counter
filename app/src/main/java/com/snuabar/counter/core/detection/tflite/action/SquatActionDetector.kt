package com.snuabar.counter.core.detection.tflite.action

/**
 * Squat detector.
 *
 * Detection logic (based on knee angle):
 * - IDLE: waiting for legs to straighten (knee angle > 170°)
 * - IN_PROGRESS: squatting down (knee angle < 100°)
 * - COMPLETED: legs straighten again (knee angle > 170°) after squatting
 *
 * Keypoint indices (COCO format):
 * - 11: left_hip, 12: right_hip
 * - 13: left_knee, 14: right_knee
 * - 15: left_ankle, 16: right_ankle
 */
class SquatActionDetector : BasePoseActionDetector(ActionType.SQUAT) {

    // Thresholds for squat detection
    private val straightLegThreshold = 170f  // Legs considered straight above this angle
    private val bentLegThreshold = 100f        // Legs considered bent below this angle
    private val minConfidence = 0.5f

    override fun detect(keypoints: Array<FloatArray>): PoseActionResult? {
        updateCooldown()

        // Extract keypoints
        val leftHip = keypoints[11]
        val leftKnee = keypoints[13]
        val leftAnkle = keypoints[15]
        val rightHip = keypoints[12]
        val rightKnee = keypoints[14]
        val rightAnkle = keypoints[16]

        // Check visibility
        if (!isVisible(leftHip, minConfidence) || !isVisible(leftKnee, minConfidence) ||
            !isVisible(leftAnkle, minConfidence) || !isVisible(rightHip, minConfidence) ||
            !isVisible(rightKnee, minConfidence) || !isVisible(rightAnkle, minConfidence)
        ) {
            return null // Not enough keypoints visible
        }

        // Calculate knee angles (hip -> knee -> ankle)
        val leftKneeAngle = calculateAngle(
            leftHip[0], leftHip[1],
            leftKnee[0], leftKnee[1],
            leftAnkle[0], leftAnkle[1]
        )
        val rightKneeAngle = calculateAngle(
            rightHip[0], rightHip[1],
            rightKnee[0], rightKnee[1],
            rightAnkle[0], rightAnkle[1]
        )

        // Use average of both knees
        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2f
        confidence = (leftKnee[2] + rightKnee[2]) / 2f

        return when (currentState) {
            ActionState.IDLE -> {
                // Waiting for legs to be straight (standing position)
                if (avgKneeAngle > straightLegThreshold) {
                    currentState = ActionState.IN_PROGRESS
                    PoseActionResult(actionType, false, currentState, count, confidence, "Standing - ready to squat")
                } else {
                    PoseActionResult(actionType, false, currentState, count, confidence, "Waiting for standing position")
                }
            }
            ActionState.IN_PROGRESS -> {
                // Squatting down
                if (avgKneeAngle < bentLegThreshold) {
                    currentState = ActionState.COMPLETED
                    PoseActionResult(actionType, false, currentState, count, confidence, "Squat depth reached")
                } else {
                    PoseActionResult(actionType, false, currentState, count, confidence, "Squatting down...")
                }
            }
            ActionState.COMPLETED -> {
                // Standing back up
                if (avgKneeAngle > straightLegThreshold) {
                    if (!isInCooldown()) {
                        count++
                        startCooldown()
                        currentState = ActionState.IDLE
                        PoseActionResult(actionType, true, currentState, count, confidence, "Squat completed!")
                    } else {
                        PoseActionResult(actionType, false, currentState, count, confidence, "Cooldown...")
                    }
                } else {
                    PoseActionResult(actionType, false, currentState, count, confidence, "Standing up...")
                }
            }
        }
    }
}
