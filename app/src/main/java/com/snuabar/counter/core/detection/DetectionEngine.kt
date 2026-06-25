package com.snuabar.counter.core.detection

import com.snuabar.counter.core.detection.tflite.PoseModelConfig
import com.snuabar.counter.core.detection.tflite.action.ActionType
import com.snuabar.counter.domain.model.SessionMode
import kotlinx.coroutines.flow.Flow

interface DetectionEngine {
    fun start(config: DetectionConfig)
    fun pause()
    fun resume()
    fun stop()
    fun setThreshold(threshold: Float)
    val countEvents: Flow<CountEvent>
}

interface DetectionEngineFactory {
    fun create(sensorType: SensorType, engineType: EngineType = EngineType.OPEN_CV): DetectionEngine
}

data class DetectionConfig(
    val sensorType: SensorType,
    val templateId: Long? = null,
    val threshold: Float = 0.7f,
    val mode: SessionMode = SessionMode.COUNTING,
    val targetSeconds: Int? = null,
    val targetResolution: android.util.Size = android.util.Size(640, 480),
    val poseModelConfig: PoseModelConfig = PoseModelConfig.STANDARD,
    val actionType: ActionType = ActionType.PUSH_UP
)

enum class SensorType { VISION, AUDIO }

enum class EngineType { OPEN_CV, TFLITE }

data class CountEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val count: Int,
    val confidence: Float
)
