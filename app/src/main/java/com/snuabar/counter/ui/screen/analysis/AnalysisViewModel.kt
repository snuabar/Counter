package com.snuabar.counter.ui.screen.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.domain.model.*
import com.snuabar.counter.domain.repository.CountingSessionRepository
import com.snuabar.counter.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val countingSessionRepository: CountingSessionRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _statistics = MutableStateFlow(SessionStatistics())
    val statistics: StateFlow<SessionStatistics> = _statistics.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.currentUserId.collect { userId ->
                userId?.let {
                    countingSessionRepository.getSessionsByUserId(it).collect { sessions ->
                        _statistics.value = calculateStatistics(sessions)
                    }
                } ?: run {
                    _statistics.value = SessionStatistics()
                }
            }
        }
    }

    private fun calculateStatistics(sessions: List<CountingSession>): SessionStatistics {
        if (sessions.isEmpty()) return SessionStatistics()

        val completedSessions = sessions.filter { it.status == SessionStatus.COMPLETED }
        val totalCount = sessions.sumOf { it.finalCount }
        val totalDuration = completedSessions.sumOf {
            if (it.endTime != null) it.endTime - it.startTime else 0L
        }
        val avgCount = if (completedSessions.isNotEmpty()) {
            completedSessions.sumOf { it.finalCount }.toDouble() / completedSessions.size
        } else 0.0

        // Daily stats (last 7 days)
        val dailyStats = calculateDailyStats(sessions)

        // Sensor type distribution
        val sensorDistribution = sessions.groupBy { it.sensorType }
            .mapValues { it.value.sumOf { s -> s.finalCount } }

        return SessionStatistics(
            totalSessions = sessions.size,
            totalCount = totalCount,
            totalDurationMs = totalDuration,
            avgCountPerSession = avgCount,
            dailyStats = dailyStats,
            sensorTypeDistribution = sensorDistribution
        )
    }

    private fun calculateDailyStats(sessions: List<CountingSession>): List<DailyStat> {
        val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis

        // Generate last 7 days
        val result = mutableListOf<DailyStat>()
        for (i in 6 downTo 0) {
            calendar.timeInMillis = today
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dayStart = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val dayEnd = dayStart + 24 * 60 * 60 * 1000

            val daySessions = sessions.filter { it.startTime in dayStart until dayEnd }
            val dayCount = daySessions.sumOf { it.finalCount }

            result.add(
                DailyStat(
                    dateLabel = dateFormat.format(Date(dayStart)),
                    count = dayCount,
                    sessions = daySessions.size
                )
            )
        }
        return result
    }
}
