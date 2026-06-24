package com.snuabar.counter.core.detection

data class DetectionConfig(
    val threshold: Float = 0.5f,
    val sampleRate: Int = 30,
    val bufferSize: Int = 1024
)
