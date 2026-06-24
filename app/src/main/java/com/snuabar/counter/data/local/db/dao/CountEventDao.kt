package com.snuabar.counter.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.snuabar.counter.data.local.db.entity.CountEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CountEventDao {
    @Insert
    suspend fun insert(event: CountEventEntity): Long

    @Query("SELECT * FROM count_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionId(sessionId: Long): Flow<List<CountEventEntity>>

    @Query("SELECT * FROM count_events WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBySessionId(sessionId: Long): CountEventEntity?
}
