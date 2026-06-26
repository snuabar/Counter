package com.snuabar.counter.domain.model

data class SessionStatistics(
    val totalSessions: Int = 0,
    val totalCount: Int = 0,
    val totalDurationMs: Long = 0,
    val avgCountPerSession: Double = 0.0,
    val dailyStats: List<DailyStat> = emptyList(),
    val sensorTypeDistribution: Map<SensorType, Int> = emptyMap(),
    val templateStats: Map<String, TemplateStat> = emptyMap()
)

data class DailyStat(
    val dateLabel: String,
    val count: Int,
    val sessions: Int
)

data class TemplateStat(
    val sessionCount: Int,
    val totalReps: Int,
    val avgReps: Float
)
