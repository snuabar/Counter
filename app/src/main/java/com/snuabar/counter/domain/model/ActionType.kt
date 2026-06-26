package com.snuabar.counter.domain.model

/**
 * Types of detectable actions.
 * Only includes repeat-counting actions; timer-based activities (e.g. plank)
 * use SessionMode.TIMER with a template instead.
 */
enum class ActionType {
    PUSH_UP,      // 俯卧撑
    SQUAT,        // 深蹲
    CUSTOM        // 用户自定义模板
}
