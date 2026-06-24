package com.snuabar.counter.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.snuabar.counter.data.local.db.entity.CountingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CountingSessionDao {
    @Insert
    suspend fun insert(session: CountingSessionEntity): Long

    @Update
    suspend fun update(session: CountingSessionEntity)

    @Query("SELECT * FROM counting_sessions WHERE id = :id")
    suspend fun getById(id: Long): CountingSessionEntity?

    @Query("SELECT * FROM counting_sessions WHERE userId = :userId ORDER BY startTime DESC")
    fun getByUserId(userId: Long): Flow<List<CountingSessionEntity>>

    @Query("SELECT * FROM counting_sessions ORDER BY startTime DESC")
    fun getAll(): Flow<List<CountingSessionEntity>>
}
