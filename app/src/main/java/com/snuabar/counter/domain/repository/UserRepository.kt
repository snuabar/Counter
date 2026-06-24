package com.snuabar.counter.domain.repository

import com.snuabar.counter.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun createUser(name: String): Long
    suspend fun deleteUser(userId: Long)
    suspend fun getUser(userId: Long): User?
    fun getAllUsers(): Flow<List<User>>
    suspend fun ensureDefaultUser(): Long

    val currentUserId: Flow<Long?>
    suspend fun setCurrentUser(userId: Long)
    suspend fun clearCurrentUser()
}
