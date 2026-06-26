package com.snuabar.counter.ui.screen.counting

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.core.detection.tflite.action.ActionState
import com.snuabar.counter.ui.component.PoseCameraPreview

@Composable
fun CountingScreen(
    viewModel: CountingViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val currentCount by viewModel.currentCount.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val confidence by viewModel.confidence.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isTimerMode by viewModel.isTimerMode.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val targetSeconds by viewModel.targetSeconds.collectAsState()
    val targetResolution by viewModel.targetResolution.collectAsState()
    val actionType by viewModel.actionType.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val availableCameras by viewModel.availableCameras.collectAsState()
    val selectedCameraId by viewModel.selectedCameraId.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val selectedTemplate by viewModel.selectedTemplate.collectAsState()
    val sessionMode by viewModel.sessionMode.collectAsState()
    val targetCount by viewModel.targetCount.collectAsState()
    val keypoints by viewModel.keypoints.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()

    // Debug panel visibility (can be toggled in real app, hardcoded for now)

    // Haptic feedback on count change
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    LaunchedEffect(currentCount) {
        if (currentCount > 0 && isRunning) {
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(50)
                }
            }
        }
    }

    // Determine effective camera ID (default to physical camera 2 = wide angle back)
    val effectiveCameraId = selectedCameraId ?: if (isFrontCamera) "1" else "2"

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview (full screen)
        PoseCameraPreview(
            cameraId = effectiveCameraId,
            onBitmap = { bitmap -> viewModel.processBitmap(bitmap) },
            onCameraReady = { viewModel.setupDetection() },
            onCameraDisposed = { viewModel.stopDetection() },
            keypoints = keypoints,
            showCameraSwitch = true,
            availableCameras = availableCameras,
            selectedCameraId = selectedCameraId,
            isFrontCamera = isFrontCamera,
            onCameraSwitch = { viewModel.selectCamera(it) },
            onToggleCamera = { viewModel.toggleCamera() },
            modifier = Modifier.fillMaxSize()
        )

        // Bottom UI section (overlay on preview)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
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

            Spacer(modifier = Modifier.height(16.dp))

            // Middle section: Large count or timer display
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isTimerMode) {
                    val minutes = elapsedSeconds / 60
                    val seconds = elapsedSeconds % 60
                    Text(
                        text = "%02d:%02d".format(minutes, seconds),
                        fontSize = 80.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.animateContentSize()
                    )
                    targetSeconds?.let { target ->
                        val targetMin = target / 60
                        val targetSec = target % 60
                        Text(
                            text = "目标: %02d:%02d".format(targetMin, targetSec),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    // Animated count display with scale effect
                    val scale by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "countScale"
                    )
                    Text(
                        text = currentCount.toString(),
                        fontSize = 120.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                    Text(
                        text = "次",
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action type selector (only when not running)
            if (!isRunning) {
                // Template selection (required)
                if (templates.isNotEmpty()) {
                    var templateExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { templateExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedTemplate?.let { "模板: ${it.name}" } ?: "选择模板",
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = templateExpanded,
                            onDismissRequest = { templateExpanded = false }
                        ) {
                            templates.forEach { template ->
                                DropdownMenuItem(
                                    text = { 
                                        Text("${template.name} (${if (template.mode == com.snuabar.counter.domain.model.SessionMode.TIMER) "计时" else "计数"})") 
                                    },
                                    onClick = {
                                        viewModel.selectTemplate(template)
                                        templateExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Target settings with +/- buttons
                if (sessionMode == com.snuabar.counter.domain.model.SessionMode.COUNTING) {
                    // Target count with +/- buttons
                    val currentTargetCount = targetCount ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("目标: ", style = MaterialTheme.typography.bodyMedium)
                        IconButton(
                            onClick = { 
                                val newValue = (currentTargetCount - 1).coerceAtLeast(0)
                                viewModel.setTargetCount(if (newValue == 0) null else newValue)
                            }
                        ) {
                            Text("-", fontSize = 20.sp)
                        }
                        Text(
                            text = if (currentTargetCount == 0) "无限" else "$currentTargetCount 次",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        IconButton(
                            onClick = { 
                                viewModel.setTargetCount(currentTargetCount + 1)
                            }
                        ) {
                            Text("+", fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (sessionMode == com.snuabar.counter.domain.model.SessionMode.TIMER) {
                    // Target time with +/- buttons (in 30 second increments)
                    val currentTargetSeconds = targetSeconds ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("目标: ", style = MaterialTheme.typography.bodyMedium)
                        IconButton(
                            onClick = { 
                                val newValue = (currentTargetSeconds - 30).coerceAtLeast(0)
                                viewModel.setTargetSeconds(if (newValue == 0) null else newValue)
                            }
                        ) {
                            Text("-", fontSize = 20.sp)
                        }
                        Text(
                            text = if (currentTargetSeconds == 0) "无限" else "${currentTargetSeconds / 60}:${"%02d".format(currentTargetSeconds % 60)}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        IconButton(
                            onClick = { 
                                viewModel.setTargetSeconds(currentTargetSeconds + 30)
                            }
                        ) {
                            Text("+", fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Show current action during counting
                val actionName = selectedTemplate?.name ?: "自定义"
                Text(
                    text = "当前: $actionName (${if (isTimerMode) "计时" else "计数"})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom section: Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (!isRunning) {
                    Button(
                        onClick = { viewModel.startCounting() },
                        enabled = selectedTemplate != null
                    ) {
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

            // Debug info panel (shown when running and debug info available)
            val currentDebugInfo = debugInfo
            if (isRunning && currentDebugInfo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                DebugInfoPanel(debugInfo = currentDebugInfo)
            }
        }
    }
}

/**
 * Debug info panel showing real-time detection parameters.
 */
@Composable
private fun DebugInfoPanel(
    debugInfo: com.snuabar.counter.core.detection.tflite.action.DetectionDebugInfo
) {
    val stateColor = when (debugInfo.state) {
        ActionState.IDLE -> MaterialTheme.colorScheme.outline
        ActionState.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        ActionState.COMPLETED -> MaterialTheme.colorScheme.tertiary
    }
    val stateText = when (debugInfo.state) {
        ActionState.IDLE -> "等待"
        ActionState.IN_PROGRESS -> "进行中"
        ActionState.COMPLETED -> "完成"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "调试信息",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "角度: ${debugInfo.currentAngle.toInt()}°",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "阈值: ${debugInfo.bentThreshold.toInt()}-${debugInfo.straightThreshold.toInt()}°",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "状态: $stateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = stateColor
                    )
                    Text(
                        text = "冷却: ${debugInfo.cooldownCounter}/${debugInfo.cooldownFrames}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = "置信度: ${(debugInfo.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = debugInfo.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
