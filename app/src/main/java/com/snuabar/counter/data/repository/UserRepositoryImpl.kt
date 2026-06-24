package com.snuabar.counter.data.repository

import com.snuabar.counter.data.local.db.dao.UserDao
import com.snuabar.counter.data.local.db.entity.UserEntity
import com.snuabar.counter.data.local.prefs.UserPreferences
import com.snuabar.counter.domain.model.User
import com.snuabar.counter.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val userPreferences: UserPreferences
) : UserRepository {

    override suspend fun createUser(name: String): Long {
        val entity = UserEntity(name = name)
        return userDao.insert(entity)
    }

    override suspend fun deleteUser(userId: Long) {
        userDao.delete(userId)
    }

    override suspend fun getUser(userId: Long): User? {
        return userDao.getById(userId)?.toDomain()
    }

    override fun getAllUsers(): Flow<List<User>> {
        return userDao.getAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun ensureDefaultUser(): Long {
        val users = userDao.getAll().first()
        return if (users.isEmpty()) {
            val entity = UserEntity(name = "默认用户")
            val userId = userDao.insert(entity)
            userPreferences.setCurrentUserId(userId)
            userId
        } else {
            val currentId = userPreferences.currentUserId.first()
            if (currentId == null) {
                val userId = users.first().id
                userPreferences.setCurrentUserId(userId)
            }
            currentId ?: users.first().id
        }
    }

    override val currentUserId: Flow<Long?> = userPreferences.currentUserId

    override suspend fun setCurrentUser(userId: Long) {
        userPreferences.setCurrentUserId(userId)
    }

    override suspend fun clearCurrentUser() {
        userPreferences.clearCurrentUser()
    }

    private fun UserEntity.toDomain() = User(
        id = id,
        name = name,
        createdAt = createdAt,
        avatarPath = avatarPath
    )
}
