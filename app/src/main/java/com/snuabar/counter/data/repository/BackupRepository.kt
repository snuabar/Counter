package com.snuabar.counter.data.repository

import android.content.Context
import com.snuabar.counter.data.local.db.CounterDatabase
import com.snuabar.counter.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val context: Context,
    private val database: CounterDatabase
) {

    private val json = Json { prettyPrint = true }

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val users = database.userDao().getAll().first()
        val sessions = database.countingSessionDao().getAll().first()
        val templates = database.templateDao().getAll().first()

        val backupData = BackupData(
            users = users.map {
                BackupUser(it.id, it.name, it.createdAt, it.avatarPath)
            },
            sessions = sessions.map {
                BackupSession(
                    it.id, it.userId, it.name, it.templateId, it.sensorType,
                    it.startTime, it.endTime, it.targetCount, it.finalCount, it.status
                )
            },
            events = emptyList(), // Simplified: events not included in backup
            templates = templates.map {
                BackupTemplate(
                    it.id, it.userId, it.name, it.type, it.sensorType,
                    it.mediaPath, it.threshold, it.createdAt
                )
            }
        )

        json.encodeToString(backupData)
    }

    suspend fun importFromJson(jsonString: String) = withContext(Dispatchers.IO) {
        val backupData = json.decodeFromString<BackupData>(jsonString)

        // Import users
        backupData.users.forEach {
            database.userDao().insert(
                com.snuabar.counter.data.local.db.entity.UserEntity(
                    id = it.id,
                    name = it.name,
                    createdAt = it.createdAt,
                    avatarPath = it.avatarPath
                )
            )
        }

        // Import templates
        backupData.templates.forEach {
            database.templateDao().insert(
                com.snuabar.counter.data.local.db.entity.TemplateEntity(
                    id = it.id,
                    userId = it.userId,
                    name = it.name,
                    type = it.type,
                    sensorType = it.sensorType,
                    mediaPath = it.mediaPath,
                    threshold = it.threshold,
                    createdAt = it.createdAt
                )
            )
        }

        // Import sessions
        backupData.sessions.forEach {
            database.countingSessionDao().insert(
                com.snuabar.counter.data.local.db.entity.CountingSessionEntity(
                    id = it.id,
                    userId = it.userId,
                    name = it.name,
                    templateId = it.templateId,
                    sensorType = it.sensorType,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    targetCount = it.targetCount,
                    finalCount = it.finalCount,
                    status = it.status
                )
            )
        }
    }

    @Serializable
    data class BackupData(
        val users: List<BackupUser>,
        val sessions: List<BackupSession>,
        val events: List<BackupEvent>,
        val templates: List<BackupTemplate>
    )

    @Serializable
    data class BackupUser(
        val id: Long,
        val name: String,
        val createdAt: Long,
        val avatarPath: String?
    )

    @Serializable
    data class BackupSession(
        val id: Long,
        val userId: Long,
        val name: String,
        val templateId: Long?,
        val sensorType: String,
        val startTime: Long,
        val endTime: Long?,
        val targetCount: Int?,
        val finalCount: Int,
        val status: String
    )

    @Serializable
    data class BackupEvent(
        val id: Long,
        val sessionId: Long,
        val timestamp: Long,
        val count: Int,
        val confidence: Float
    )

    @Serializable
    data class BackupTemplate(
        val id: Long,
        val userId: Long?,
        val name: String,
        val type: String,
        val sensorType: String,
        val mediaPath: String?,
        val threshold: Float,
        val createdAt: Long
    )
}
