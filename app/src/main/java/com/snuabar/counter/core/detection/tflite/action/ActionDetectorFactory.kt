package com.snuabar.counter.core.detection.tflite.action

import com.snuabar.counter.domain.model.ActionType
import com.snuabar.counter.domain.model.Template

/**
 * Factory for creating action detectors.
 * Maps ActionType to concrete detector implementations.
 */
object ActionDetectorFactory {
    
    /** Create a detector for the specified action type. */
    fun createDetector(actionType: ActionType, template: Template? = null): PoseActionDetector {
        return when (actionType) {
            ActionType.PUSH_UP -> PushUpActionDetector()
            ActionType.SQUAT -> SquatActionDetector()
            ActionType.CUSTOM -> {
                if (template != null) {
                    CustomPoseActionDetector(template)
                } else {
                    // Fallback: return a no-op detector if no template provided
                    CustomPoseActionDetector(
                        Template(name = "default", type = com.snuabar.counter.domain.model.TemplateType.CUSTOM,
                            sensorType = com.snuabar.counter.domain.model.SensorType.VISION)
                    )
                }
            }
        }
    }
    
    /** Get all available action types with their display names. */
    fun getAvailableActions(): List<Pair<ActionType, String>> {
        return listOf(
            ActionType.PUSH_UP to "俯卧撑",
            ActionType.SQUAT to "深蹲",
            ActionType.CUSTOM to "自定义模板"
        )
    }
}
