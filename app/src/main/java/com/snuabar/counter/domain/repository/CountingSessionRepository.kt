package com.snuabar.counter.domain.repository

import com.snuabar.counter.domain.model.CountEvent
import com.snuabar.counter.domain.model.CountingSession
import kotlinx.coroutines.flow.Flow

interface CountingSessionRepository {
    suspend fun createSession(session: CountingSession): Long
    suspend fun updateSession(session: CountingSession)
    suspend fun getSession(id: Long): CountingSession?
    fun getAllSessions(): Flow<List<CountingSession>>
    suspend fun addCountEvent(event: CountEvent)
    fun getCountEvents(sessionId: Long): Flow<List<CountEvent>>
}
