package com.snuabar.counter.core.detection

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
    fun create(sensorType: SensorType): DetectionEngine
}

data class DetectionConfig(
    val sensorType: SensorType,
    val templateId: Long? = null,
    val threshold: Float = 0.7f
)

enum class SensorType { VISION, AUDIO }

data class CountEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val count: Int,
    val confidence: Float
)
