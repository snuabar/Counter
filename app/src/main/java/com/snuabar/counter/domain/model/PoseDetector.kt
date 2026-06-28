package com.snuabar.counter.domain.model

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Detects body pose type from COCO keypoints.
 *
 * COCO keypoint indices:
 * - 5: left_shoulder, 6: right_shoulder
 * - 11: left_hip, 12: right_hip
 * - 13: left_knee, 14: right_knee
 * - 15: left_ankle, 16: right_ankle
 */
object PoseDetector {

    /**
     * Detect the pose type from keypoints.
     *
     * Strategy:
     * 1. Compute the body axis (shoulder center to hip center).
     * 2. If axis is mostly vertical -> STANDING
     * 3. If axis is mostly horizontal -> PRONE or SUPINE (both treated similarly for matching)
     * 4. If ambiguous -> UNKNOWN
     */
    fun detectPose(keypoints: Array<FloatArray>): PoseType {
        if (keypoints.size < 13) return PoseType.UNKNOWN

        val minConfidence = 0.3f

        // Get shoulder and hip centers
        val leftShoulder = keypoints[5]
        val rightShoulder = keypoints[6]
        val leftHip = keypoints[11]
        val rightHip = keypoints[12]

        // Check visibility
        val required = arrayOf(leftShoulder, rightShoulder, leftHip, rightHip)
        if (required.any { it.size < 3 || it[2] < minConfidence }) {
            return PoseType.UNKNOWN
        }

        val shoulderCenterX = (leftShoulder[0] + rightShoulder[0]) / 2f
        val shoulderCenterY = (leftShoulder[1] + rightShoulder[1]) / 2f
        val hipCenterX = (leftHip[0] + rightHip[0]) / 2f
        val hipCenterY = (leftHip[1] + rightHip[1]) / 2f

        // Body axis angle (in degrees, 0 = horizontal right, 90 = vertical down)
        val dx = hipCenterX - shoulderCenterX
        val dy = hipCenterY - shoulderCenterY
        val angle = abs(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))) % 180.0

        // Classify based on angle
        // Vertical axis: angle near 90 (shoulder above hip in image coords)
        // Horizontal axis: angle near 0 or 180
        return when {
            angle in 60.0..120.0 -> PoseType.STANDING
            angle < 30.0 || angle > 150.0 -> PoseType.PRONE // Horizontal, treat as prone/supine group
            else -> PoseType.UNKNOWN
        }
    }

    /**
     * Compute a pose confidence score [0,1].
     * Higher means we are more certain about the detected pose.
     */
    fun computePoseConfidence(keypoints: Array<FloatArray>): Float {
        if (keypoints.size < 13) return 0f

        val minConfidence = 0.3f
        val requiredIndices = listOf(5, 6, 11, 12)

        var totalConfidence = 0f
        var validCount = 0

        for (idx in requiredIndices) {
            val kp = keypoints[idx]
            if (kp.size >= 3 && kp[2] >= minConfidence) {
                totalConfidence += kp[2]
                validCount++
            }
        }

        return if (validCount == requiredIndices.size) totalConfidence / validCount else 0f
    }

    /**
     * Determine if two pose types are compatible for matching.
     * PRONE and SUPINE are treated as the same horizontal group.
     */
    fun isPoseCompatible(templatePose: PoseType, currentPose: PoseType): Boolean {
        if (templatePose == PoseType.UNKNOWN || currentPose == PoseType.UNKNOWN) {
            return true // Allow unknown to match anything
        }
        if (templatePose == currentPose) return true

        // PRONE and SUPINE are in the same horizontal group
        val horizontalPoses = setOf(PoseType.PRONE, PoseType.SUPINE)
        return templatePose in horizontalPoses && currentPose in horizontalPoses
    }
}
