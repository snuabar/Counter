package com.snuabar.counter.core.detection.tflite.action

import com.snuabar.counter.domain.model.ActionType

/**
 * Debug information for pose action detection.
 */
data class DetectionDebugInfo(
    val currentAngle: Float = 0f,
    val straightThreshold: Float = 0f,
    val bentThreshold: Float = 0f,
    val state: ActionState = ActionState.IDLE,
    val cooldownCounter: Int = 0,
    val cooldownFrames: Int = 0,
    val confidence: Float = 0f,
    val message: String = ""
)

/**
 * Pose action detection result
 */
data class PoseActionResult(
    val actionType: ActionType,
    val isDetected: Boolean,
    val currentState: ActionState,
    val count: Int,
    val confidence: Float = 0f,
    val debugInfo: String = "",
    val structuredDebugInfo: DetectionDebugInfo = DetectionDebugInfo(),
    /** Velocity matching score (0~1). Higher means the current window's speed pattern
     *  matches the template better. Used for "too fast / too slow" feedback. */
    val velocityScore: Float = 0f
)

enum class ActionState {
    IDLE,         // 等待开始
    IN_PROGRESS,  // 动作进行中
    COMPLETED     // 动作完成（计数+1）
}
