package com.snuabar.counter.domain.model

enum class TemplateType { BUILTIN, CUSTOM }

data class Template(
    val id: Long = 0,
    val userId: Long? = null, // null 表示内置模板
    val name: String,
    val type: TemplateType,
    val sensorType: SensorType,
    val mediaPath: String? = null,
    val featureVector: ByteArray? = null,
    val threshold: Float = 0.7f,
    val createdAt: Long = System.currentTimeMillis()
)
