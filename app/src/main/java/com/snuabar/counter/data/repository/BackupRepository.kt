package com.snuabar.counter.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.snuabar.counter.data.local.db.CounterDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CounterDatabase
) {

    private val gson = Gson()

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

        gson.toJson(backupData)
    }

    suspend fun importFromJson(jsonString: String) = withContext(Dispatchers.IO) {
        val backupData = gson.fromJson(jsonString, BackupData::class.java)

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

    data class BackupData(
        @SerializedName("users") val users: List<BackupUser>,
        @SerializedName("sessions") val sessions: List<BackupSession>,
        @SerializedName("events") val events: List<BackupEvent>,
        @SerializedName("templates") val templates: List<BackupTemplate>
    )

    data class BackupUser(
        @SerializedName("id") val id: Long,
        @SerializedName("name") val name: String,
        @SerializedName("createdAt") val createdAt: Long,
        @SerializedName("avatarPath") val avatarPath: String?
    )

    data class BackupSession(
        @SerializedName("id") val id: Long,
        @SerializedName("userId") val userId: Long,
        @SerializedName("name") val name: String,
        @SerializedName("templateId") val templateId: Long?,
        @SerializedName("sensorType") val sensorType: String,
        @SerializedName("startTime") val startTime: Long,
        @SerializedName("endTime") val endTime: Long?,
        @SerializedName("targetCount") val targetCount: Int?,
        @SerializedName("finalCount") val finalCount: Int,
        @SerializedName("status") val status: String
    )

    data class BackupEvent(
        @SerializedName("id") val id: Long,
        @SerializedName("sessionId") val sessionId: Long,
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("count") val count: Int,
        @SerializedName("confidence") val confidence: Float
    )

    data class BackupTemplate(
        @SerializedName("id") val id: Long,
        @SerializedName("userId") val userId: Long?,
        @SerializedName("name") val name: String,
        @SerializedName("type") val type: String,
        @SerializedName("sensorType") val sensorType: String,
        @SerializedName("mediaPath") val mediaPath: String?,
        @SerializedName("threshold") val threshold: Float,
        @SerializedName("createdAt") val createdAt: Long
    )
}
