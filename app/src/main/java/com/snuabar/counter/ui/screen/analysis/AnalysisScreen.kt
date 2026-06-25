package com.snuabar.counter.ui.screen.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.SessionStatistics
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val statistics by viewModel.statistics.collectAsState()
    val timeRange by viewModel.timeRange.collectAsState()
    val dailyCounts by viewModel.dailyCounts.collectAsState()
    val templateStats by viewModel.templateStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据分析") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (statistics.totalSessions == 0) {
                EmptyAnalysisState()
            } else {
                SummaryCards(statistics)
                TimeRangeSelector(timeRange, viewModel::setTimeRange)
                TrendLineChart(dailyCounts, timeRange)
                TemplateStatsCard(templateStats)
                SensorTypeDistributionCard(statistics.sensorTypeDistribution)
            }
        }
    }
}

@Composable
private fun EmptyAnalysisState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无数据\n开始计数后，数据分析将显示在这里",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SummaryCards(statistics: SessionStatistics) {
    val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(statistics.totalDurationMs).toInt()

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "统计概览",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryCard(
                title = "总次数",
                value = statistics.totalCount.toString(),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            SummaryCard(
                title = "会话数",
                value = statistics.totalSessions.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryCard(
                title = "总时长(分)",
                value = durationMinutes.toString(),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            SummaryCard(
                title = "平均次数/会话",
                value = "%.1f".format(statistics.avgCountPerSession),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangeSelector(currentRange: TimeRange, onRangeSelected: (TimeRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(TimeRange.WEEK, TimeRange.MONTH, TimeRange.YEAR).forEach { range ->
            FilterChip(
                selected = currentRange == range,
                onClick = { onRangeSelected(range) },
                label = {
                    Text(
                        when (range) {
                            TimeRange.WEEK -> "周"
                            TimeRange.MONTH -> "月"
                            TimeRange.YEAR -> "年"
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun TrendLineChart(dailyCounts: List<DailyCount>, timeRange: TimeRange) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "计数趋势",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (dailyCounts.isNotEmpty()) {
                LineChart(dailyCounts)
            } else {
                Text(
                    text = "暂无趋势数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            }
        }
    }
}

@Composable
private fun LineChart(dailyCounts: List<DailyCount>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val maxValue = dailyCounts.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    // Show a subset of labels to avoid overcrowding
    val labelStep = when {
        dailyCounts.size <= 7 -> 1
        dailyCounts.size <= 31 -> 5
        else -> 30
    }

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height
            val padding = 8.dp.toPx()
            val drawableWidth = chartWidth - padding * 2
            val drawableHeight = chartHeight - padding * 2

            if (dailyCounts.size < 2) return@Canvas

            val stepX = drawableWidth / (dailyCounts.size - 1)

            // Draw line path
            val path = Path()
            dailyCounts.forEachIndexed { index, dailyCount ->
                val x = padding + index * stepX
                val y = padding + drawableHeight - (dailyCount.count.toFloat() / maxValue * drawableHeight)
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw data points
            dailyCounts.forEachIndexed { index, dailyCount ->
                val x = padding + index * stepX
                val y = padding + drawableHeight - (dailyCount.count.toFloat() / maxValue * drawableHeight)
                drawCircle(
                    color = primaryColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }

        // X-axis labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dailyCounts.forEachIndexed { index, dailyCount ->
                if (index % labelStep == 0 || index == dailyCounts.size - 1) {
                    Text(
                        text = dailyCount.date,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = outlineColor,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateStatsCard(templateStats: List<TemplateStatItem>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "模板使用统计",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (templateStats.isEmpty()) {
                Text(
                    text = "暂无模板数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                templateStats.forEach { stat ->
                    TemplateStatRow(stat)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun TemplateStatRow(stat: TemplateStatItem) {
    Column {
        Text(
            text = stat.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "会话: ${stat.sessionCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "总计: ${stat.totalReps}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "平均: %.1f".format(stat.avgReps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SensorTypeDistributionCard(distribution: Map<SensorType, Int>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "检测模式分布",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val total = distribution.values.sum().coerceAtLeast(1)

            distribution.forEach { (sensorType, count) ->
                val percentage = count.toFloat() / total
                SensorTypeBar(
                    label = when (sensorType) {
                        SensorType.VISION -> "视觉检测"
                        SensorType.AUDIO -> "音频检测"
                    },
                    count = count,
                    percentage = percentage,
                    color = when (sensorType) {
                        SensorType.VISION -> MaterialTheme.colorScheme.primary
                        SensorType.AUDIO -> MaterialTheme.colorScheme.secondary
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SensorTypeBar(label: String, count: Int, percentage: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$count (${(percentage * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = percentage,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
