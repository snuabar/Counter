package com.snuabar.counter.domain.model

data class CountEvent(
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val count: Int,
    val confidence: Float
)
