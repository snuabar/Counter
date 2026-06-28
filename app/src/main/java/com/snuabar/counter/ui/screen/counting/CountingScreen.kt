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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.core.detection.tflite.action.ActionState
import com.snuabar.counter.ui.component.CountdownOverlay
import com.snuabar.counter.ui.component.PoseCameraPreview
import com.snuabar.counter.ui.component.SkeletonAnimationPreview

@Composable
private fun OutlinedText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textColor: Color = Color.White,
    outlineColor: Color = Color.Black,
    outlineWidth: Float = 4f,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        // Outline layer using stroke draw style
        Text(
            text = text,
            fontSize = fontSize,
            color = outlineColor,
            style = TextStyle(
                drawStyle = Stroke(width = outlineWidth)
            )
        )
        // Fill layer
        Text(
            text = text,
            fontSize = fontSize,
            color = textColor
        )
    }
}

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
    val fps by viewModel.fps.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val isCountingDown by viewModel.isCountingDown.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()

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

    // Determine effective camera ID (use selected or let ViewModel handle default)
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview (full screen)
        PoseCameraPreview(
            cameraId = selectedCameraId ?: "0",
            onBitmap = { bitmap -> viewModel.processBitmap(bitmap) },
            onCameraReady = { viewModel.setupDetection() },
            onCameraDisposed = { viewModel.stopDetection() },
            keypoints = keypoints,
            fps = fps,
            showCameraSwitch = true,
            availableCameras = availableCameras,
            selectedCameraId = selectedCameraId,
            isFrontCamera = isFrontCamera,
            onCameraSwitch = { viewModel.selectCamera(it) },
            onToggleCamera = { viewModel.toggleCamera() },
            modifier = Modifier.fillMaxSize()
        )

        // Countdown overlay
        if (isCountingDown) {
            CountdownOverlay(
                seconds = countdownSeconds,
                message = "准备开始计数",
                onCancel = { viewModel.cancelCountdown() }
            )
        }

        // Top-left info section: user, confidence, template name, count/timer
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)
        ) {
            // User info and confidence
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentUser?.let { "用户: ${it.name}" } ?: "未选择用户",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "置信度: ${(confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Template name
            selectedTemplate?.let { template ->
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Count/Timer display with outline (same font size as template name)
            if (isTimerMode) {
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                OutlinedText(
                    text = "%02d:%02d".format(minutes, seconds),
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    outlineWidth = 4f
                )
            } else {
                OutlinedText(
                    text = currentCount.toString(),
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    outlineWidth = 4f
                )
            }
        }

        // Bottom UI section (overlay on preview)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Template selection icon + Target settings (same row)
            if (!isRunning && !isCountingDown) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Template selection icon
                    if (templates.isNotEmpty()) {
                        var templateExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { templateExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "选择模板",
                                    tint = MaterialTheme.colorScheme.onPrimary
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
                                        },
                                        trailingIcon = if (template.keypointSequence != null) {{
                                            SkeletonAnimationPreview(
                                                keypointSequence = template.keypointSequence,
                                                modifier = Modifier.size(60.dp),
                                                frameDelayMs = 100
                                            )
                                        }} else null
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Target settings
                    if (sessionMode == com.snuabar.counter.domain.model.SessionMode.COUNTING) {
                        val currentTargetCount = targetCount ?: 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { viewModel.setTargetCount(currentTargetCount + 1) }
                            ) {
                                Text("+", fontSize = 20.sp)
                            }
                        }
                    } else if (sessionMode == com.snuabar.counter.domain.model.SessionMode.TIMER) {
                        val currentTargetSeconds = targetSeconds ?: 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { viewModel.setTargetSeconds(currentTargetSeconds + 30) }
                            ) {
                                Text("+", fontSize = 20.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (!isRunning && !isCountingDown) {
                    Button(
                        onClick = { viewModel.beginCounting() },
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
        }
    }
}
