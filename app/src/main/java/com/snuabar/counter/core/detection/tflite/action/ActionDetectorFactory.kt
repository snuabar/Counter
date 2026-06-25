package com.snuabar.counter.core.detection.tflite.action

/**
 * Factory for creating action detectors.
 * Maps ActionType to concrete detector implementations.
 */
object ActionDetectorFactory {
    
    /** Create a detector for the specified action type. */
    fun createDetector(actionType: ActionType): PoseActionDetector {
        return when (actionType) {
            ActionType.PUSH_UP -> PushUpActionDetector()
            ActionType.SQUAT -> SquatActionDetector()
            ActionType.PLANK -> PlankActionDetector()
            ActionType.CUSTOM -> CustomPoseActionDetector()
        }
    }
    
    /** Get all available action types with their display names. */
    fun getAvailableActions(): List<Pair<ActionType, String>> {
        return listOf(
            ActionType.PUSH_UP to "俯卧撑",
            ActionType.SQUAT to "深蹲",
            ActionType.PLANK to "平板支撑",
            ActionType.CUSTOM to "自定义模板"
        )
    }
}
