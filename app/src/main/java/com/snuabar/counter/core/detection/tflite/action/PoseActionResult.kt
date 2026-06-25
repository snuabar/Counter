package com.snuabar.counter.core.detection.tflite.action

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

enum class ActionType {
    PUSH_UP,      // 俯卧撑
    SQUAT,        // 深蹲
    PLANK,        // 平板支撑
    CUSTOM        // 用户自定义模板
}

enum class ActionState {
    IDLE,         // 等待开始
    IN_PROGRESS,  // 动作进行中
    COMPLETED     // 动作完成（计数+1）
}
