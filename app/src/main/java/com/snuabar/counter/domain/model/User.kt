package com.snuabar.counter.domain.model

data class User(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val avatarPath: String? = null
)
