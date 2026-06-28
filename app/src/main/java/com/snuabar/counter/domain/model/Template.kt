package com.snuabar.counter.domain.model

enum class TemplateType { BUILTIN, CUSTOM }

data class Template(
    val id: Long = 0,
    val userId: Long? = null, // null 表示内置模板
    val name: String,
    val type: TemplateType,
    val sensorType: SensorType,
    val mode: SessionMode = SessionMode.COUNTING,
    val actionType: ActionType? = null, // null means custom/timer-based
    val targetSeconds: Int? = null,
    val mediaPath: String? = null,
    val featureVector: ByteArray? = null,
    val keypointSequence: ByteArray? = null, // 原始关键点序列，用于预览动画
    val threshold: Float = 0.7f,
    val poseType: PoseType = PoseType.UNKNOWN,
    val createdAt: Long = System.currentTimeMillis()
)
