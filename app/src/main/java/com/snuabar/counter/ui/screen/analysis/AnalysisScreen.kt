package com.snuabar.counter.ui.screen.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.domain.model.DailyStat
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.SessionStatistics
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val statistics by viewModel.statistics.collectAsState()

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
                DailyChartCard(statistics.dailyStats)
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

@Composable
private fun DailyChartCard(dailyStats: List<DailyStat>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "近7天计数趋势",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (dailyStats.isNotEmpty()) {
                BarChart(dailyStats)
            }
        }
    }
}

@Composable
private fun BarChart(dailyStats: List<DailyStat>) {
    val maxValue = dailyStats.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        dailyStats.forEach { stat ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stat.count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .heightIn(min = 2.dp)
                        .height((stat.count.toFloat() / maxValue * 160).dp)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxSize()
                    ) {}
                }
                Text(
                    text = stat.dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )
            }
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
