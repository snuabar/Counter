package com.snuabar.counter.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.snuabar.counter.data.local.db.CounterDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE templates ADD COLUMN keypointSequence BLOB")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CounterDatabase {
        return Room.databaseBuilder(
            context,
            CounterDatabase::class.java,
            "counter_database"
        ).addMigrations(MIGRATION_4_5)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideUserDao(db: CounterDatabase) = db.userDao()
    @Provides
    fun provideCountingSessionDao(db: CounterDatabase) = db.countingSessionDao()
    @Provides
    fun provideCountEventDao(db: CounterDatabase) = db.countEventDao()
    @Provides
    fun provideTemplateDao(db: CounterDatabase) = db.templateDao()
}
