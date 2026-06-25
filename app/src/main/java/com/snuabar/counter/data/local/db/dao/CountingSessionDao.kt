package com.snuabar.counter.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
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

    // Daily count sums for line chart - returns date and total count per day
    @Query(
        "SELECT DATE(startTime / 1000, 'unixepoch') as date, SUM(finalCount) as totalCount " +
        "FROM counting_sessions " +
        "WHERE startTime >= :startTime AND startTime < :endTime " +
        "GROUP BY DATE(startTime / 1000, 'unixepoch') " +
        "ORDER BY date ASC"
    )
    suspend fun getDailyCountSums(startTime: Long, endTime: Long): List<DailyCountSum>

    // Template statistics - grouped by template name
    @Query(
        "SELECT t.name as templateName, " +
        "COUNT(cs.id) as sessionCount, " +
        "SUM(cs.finalCount) as totalReps, " +
        "AVG(cs.finalCount) as avgReps " +
        "FROM counting_sessions cs " +
        "LEFT JOIN templates t ON cs.templateId = t.id " +
        "WHERE cs.startTime >= :startTime AND cs.startTime < :endTime " +
        "GROUP BY t.name " +
        "ORDER BY totalReps DESC"
    )
    suspend fun getTemplateStats(startTime: Long, endTime: Long): List<TemplateStatRow>
}

// Data classes for query results
data class DailyCountSum(
    val date: String,
    val totalCount: Int
)

data class TemplateStatRow(
    val templateName: String?,
    val sessionCount: Int,
    val totalReps: Int,
    val avgReps: Float
)
