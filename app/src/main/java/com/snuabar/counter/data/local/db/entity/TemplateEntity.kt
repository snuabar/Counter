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
    val mediaPath: String? = null,
    val featureVector: ByteArray? = null,
    val threshold: Float = 0.7f,
    val createdAt: Long = System.currentTimeMillis()
)
