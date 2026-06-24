package com.snuabar.counter.data.repository

import com.snuabar.counter.data.local.db.dao.CountEventDao
import com.snuabar.counter.data.local.db.dao.CountingSessionDao
import com.snuabar.counter.data.local.db.entity.CountEventEntity
import com.snuabar.counter.data.local.db.entity.CountingSessionEntity
import com.snuabar.counter.domain.model.CountEvent
import com.snuabar.counter.domain.model.CountingSession
import com.snuabar.counter.domain.model.SessionStatus
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.repository.CountingSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CountingSessionRepositoryImpl @Inject constructor(
    private val sessionDao: CountingSessionDao,
    private val eventDao: CountEventDao
) : CountingSessionRepository {

    override suspend fun createSession(session: CountingSession): Long {
        return sessionDao.insert(session.toEntity())
    }

    override suspend fun updateSession(session: CountingSession) {
        sessionDao.update(session.toEntity())
    }

    override suspend fun getSession(id: Long): CountingSession? {
        return sessionDao.getById(id)?.toDomain()
    }

    override fun getAllSessions(): Flow<List<CountingSession>> {
        return sessionDao.getAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun addCountEvent(event: CountEvent) {
        eventDao.insert(event.toEntity())
    }

    override fun getCountEvents(sessionId: Long): Flow<List<CountEvent>> {
        return eventDao.getBySessionId(sessionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    // Mappers
    private fun CountingSession.toEntity() = CountingSessionEntity(
        id = id,
        userId = userId,
        name = name,
        templateId = templateId,
        sensorType = sensorType.name,
        startTime = startTime,
        endTime = endTime,
        targetCount = targetCount,
        finalCount = finalCount,
        status = status.name
    )

    private fun CountingSessionEntity.toDomain() = CountingSession(
        id = id,
        userId = userId,
        name = name,
        templateId = templateId,
        sensorType = SensorType.valueOf(sensorType),
        startTime = startTime,
        endTime = endTime,
        targetCount = targetCount,
        finalCount = finalCount,
        status = SessionStatus.valueOf(status)
    )

    private fun CountEvent.toEntity() = CountEventEntity(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        count = count,
        confidence = confidence
    )

    private fun CountEventEntity.toDomain() = CountEvent(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        count = count,
        confidence = confidence
    )
}
