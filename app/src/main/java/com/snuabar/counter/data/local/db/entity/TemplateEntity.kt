package com.snuabar.counter.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long? = null, // null 表示内置模板
    val name: String,
    val type: String, // BUILTIN or CUSTOM
    val sensorType: String, // VISION or AUDIO
    val mode: String = "COUNTING", // COUNTING or TIMER
    val actionType: String? = null, // PUSH_UP, SQUAT, CUSTOM, or null for timer-based
    val targetSeconds: Int? = null, // 计时模式的目标秒数
    val mediaPath: String? = null,
    val featureVector: ByteArray? = null,
    val keypointSequence: ByteArray? = null,
    val threshold: Float = 0.7f,
    val poseType: String = "UNKNOWN", // STANDING, PRONE, SUPINE, UNKNOWN
    val createdAt: Long = System.currentTimeMillis()
)
