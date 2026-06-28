package com.snuabar.counter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.snuabar.counter.data.local.db.dao.*
import com.snuabar.counter.data.local.db.entity.*

@Database(
    entities = [
        UserEntity::class,
        CountingSessionEntity::class,
        CountEventEntity::class,
        TemplateEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class CounterDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun countingSessionDao(): CountingSessionDao
    abstract fun countEventDao(): CountEventDao
    abstract fun templateDao(): TemplateDao
}
