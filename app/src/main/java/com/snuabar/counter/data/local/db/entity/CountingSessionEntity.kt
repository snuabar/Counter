package com.snuabar.counter.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "counting_sessions")
data class CountingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val templateId: Long? = null,
    val sensorType: String, // VISION or AUDIO
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val targetCount: Int? = null,
    val finalCount: Int = 0,
    val status: String = "RUNNING" // RUNNING, PAUSED, COMPLETED, CANCELLED
)
