package com.snuabar.counter.core.detection.tflite.action

import com.snuabar.counter.domain.model.ActionType
import com.snuabar.counter.domain.model.Template

/**
 * Factory for creating action detectors.
 * All detection uses template matching via CustomPoseActionDetector.
 */
object ActionDetectorFactory {

    /** Create a detector using the user's recorded template. */
    fun createDetector(template: Template?): PoseActionDetector {
        return if (template != null) {
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
