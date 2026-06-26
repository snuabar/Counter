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

enum class TimeRange { WEEK, MONTH, YEAR }

data class DailyCount(val date: String, val count: Int)

data class TemplateStatItem(
    val name: String,
    val sessionCount: Int,
    val totalReps: Int,
    val avgReps: Float
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val countingSessionRepository: CountingSessionRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _statistics = MutableStateFlow(SessionStatistics())
    val statistics: StateFlow<SessionStatistics> = _statistics.asStateFlow()

    private val _timeRange = MutableStateFlow(TimeRange.WEEK)
    val timeRange: StateFlow<TimeRange> = _timeRange.asStateFlow()

    private val _dailyCounts = MutableStateFlow<List<DailyCount>>(emptyList())
    val dailyCounts: StateFlow<List<DailyCount>> = _dailyCounts.asStateFlow()

    private val _templateStats = MutableStateFlow<List<TemplateStatItem>>(emptyList())
    val templateStats: StateFlow<List<TemplateStatItem>> = _templateStats.asStateFlow()

    private var allSessions: List<CountingSession> = emptyList()

    init {
        viewModelScope.launch {
            userRepository.currentUserId.collect { userId ->
                userId?.let {
                    countingSessionRepository.getSessionsByUserId(it).collect { sessions ->
                        allSessions = sessions
                        _statistics.value = calculateStatistics(sessions)
                        updateChartData()
                    }
                } ?: run {
                    allSessions = emptyList()
                    _statistics.value = SessionStatistics()
                    _dailyCounts.value = emptyList()
                    _templateStats.value = emptyList()
                }
            }
        }
    }

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
        updateChartData()
    }

    private fun updateChartData() {
        val range = _timeRange.value
        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis

        val dayCount = when (range) {
            TimeRange.WEEK -> 7
            TimeRange.MONTH -> 30
            TimeRange.YEAR -> 365
        }

        calendar.timeInMillis = today
        calendar.add(Calendar.DAY_OF_YEAR, -dayCount + 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = today + 24 * 60 * 60 * 1000

        val filteredSessions = allSessions.filter { it.startTime in startTime until endTime }

        // Calculate daily counts
        val dateFormat = when (range) {
            TimeRange.WEEK -> SimpleDateFormat("MM-dd", Locale.getDefault())
            TimeRange.MONTH -> SimpleDateFormat("MM-dd", Locale.getDefault())
            TimeRange.YEAR -> SimpleDateFormat("yyyy-MM", Locale.getDefault())
        }

        val dailyMap = linkedMapOf<String, Int>()
        val cal = Calendar.getInstance()

        if (range == TimeRange.YEAR) {
            // Group by month for year view
            for (i in 11 downTo 0) {
                cal.timeInMillis = today
                cal.add(Calendar.MONTH, -i)
                val key = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
                dailyMap[key] = 0
            }
        } else {
            for (i in (dayCount - 1) downTo 0) {
                cal.timeInMillis = today
                cal.add(Calendar.DAY_OF_YEAR, -i)
                val key = dateFormat.format(cal.time)
                dailyMap[key] = 0
            }
        }

        filteredSessions.forEach { session ->
            val key = if (range == TimeRange.YEAR) {
                SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(session.startTime))
            } else {
                dateFormat.format(Date(session.startTime))
            }
            dailyMap[key] = (dailyMap[key] ?: 0) + session.finalCount
        }

        _dailyCounts.value = dailyMap.map { (date, count) -> DailyCount(date, count) }

        // Calculate template stats
        val templateMap = filteredSessions.groupBy { it.name }
        _templateStats.value = templateMap.map { (name, sessions) ->
            TemplateStatItem(
                name = name,
                sessionCount = sessions.size,
                totalReps = sessions.sumOf { it.finalCount },
                avgReps = if (sessions.isNotEmpty()) {
                    sessions.sumOf { it.finalCount }.toFloat() / sessions.size
                } else 0f
            )
        }.sortedByDescending { it.totalReps }
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

        // Template stats
        val templateStatsMap = sessions.groupBy { it.name }
            .mapValues { (_, sessions) ->
                TemplateStat(
                    sessionCount = sessions.size,
                    totalReps = sessions.sumOf { it.finalCount },
                    avgReps = if (sessions.isNotEmpty()) {
                        sessions.sumOf { it.finalCount }.toFloat() / sessions.size
                    } else 0f
                )
            }

        return SessionStatistics(
            totalSessions = sessions.size,
            totalCount = totalCount,
            totalDurationMs = totalDuration,
            avgCountPerSession = avgCount,
            dailyStats = dailyStats,
            sensorTypeDistribution = sensorDistribution,
            templateStats = templateStatsMap
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
