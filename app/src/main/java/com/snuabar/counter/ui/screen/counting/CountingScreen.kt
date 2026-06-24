package com.snuabar.counter.ui.screen.counting

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CountingScreen(
    viewModel: CountingViewModel = hiltViewModel()
) {
    val currentCount by viewModel.currentCount.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val confidence by viewModel.confidence.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section: User info, status and confidence
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // User info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentUser?.let { "用户: ${it.name}" } ?: "未选择用户",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isRunning) "计数中" else "暂停",
                style = MaterialTheme.typography.titleMedium,
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            if (isRunning) {
                Text(
                    text = "置信度: ${(confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Middle section: Large count display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentCount.toString(),
                fontSize = 120.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "次",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // Bottom section: Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (!isRunning) {
                Button(onClick = { viewModel.startCounting() }) {
                    Text("开始")
                }
            } else {
                Button(onClick = { viewModel.pauseCounting() }) {
                    Text("暂停")
                }
            }

            Button(onClick = { viewModel.stopCounting() }) {
                Text("停止")
            }

            OutlinedButton(onClick = { viewModel.resetCount() }) {
                Text("重置")
            }
        }
    }
}
