package com.snuabar.counter.domain.model

enum class SensorType { VISION, AUDIO }

enum class SessionStatus { RUNNING, PAUSED, COMPLETED, CANCELLED }

data class CountingSession(
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val templateId: Long? = null,
    val sensorType: SensorType,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val targetCount: Int? = null,
    val finalCount: Int = 0,
    val status: SessionStatus = SessionStatus.RUNNING
)
