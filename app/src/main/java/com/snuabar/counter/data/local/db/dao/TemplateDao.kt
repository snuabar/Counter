package com.snuabar.counter.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.snuabar.counter.data.local.db.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity): Long

    @Update
    suspend fun update(template: TemplateEntity)

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: Long): TemplateEntity?

    @Query("SELECT * FROM templates WHERE userId = :userId OR userId IS NULL")
    fun getByUserId(userId: Long): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY createdAt DESC")
    fun getAll(): Flow<List<TemplateEntity>>

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: Long)
}
