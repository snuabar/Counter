package com.snuabar.counter.core.detection.tflite.action

import com.snuabar.counter.domain.model.ActionType

/**
 * Pose action detection result
 */
data class PoseActionResult(
    val actionType: ActionType,
    val isDetected: Boolean,
    val currentState: ActionState,
    val count: Int,
    val confidence: Float = 0f,
    val debugInfo: String = ""
)

enum class ActionState {
    IDLE,         // 等待开始
    IN_PROGRESS,  // 动作进行中
    COMPLETED     // 动作完成（计数+1）
}
