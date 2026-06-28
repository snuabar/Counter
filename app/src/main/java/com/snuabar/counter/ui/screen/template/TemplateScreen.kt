package com.snuabar.counter.ui.screen.template

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.SessionMode
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType
import com.snuabar.counter.ui.component.CountdownOverlay
import com.snuabar.counter.ui.component.PoseCameraPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    viewModel: TemplateViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsState()
    val showDialog by viewModel.showAddTemplateDialog.collectAsState()
    val newTemplateName by viewModel.newTemplateName.collectAsState()
    val selectedSensorType by viewModel.selectedSensorType.collectAsState()
    val selectedSessionMode by viewModel.selectedSessionMode.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isCountingDown by viewModel.isCountingDown.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val recordProgress by viewModel.recordProgress.collectAsState()
    val recordTargetFrames by viewModel.recordTargetFrames.collectAsState()
    val recordingTemplateName by viewModel.recordingTemplateName.collectAsState()

    val isRecordingComplete by viewModel.isRecordingComplete.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模板管理") },
                actions = {
                    if (!isRecording) {
                        IconButton(onClick = { viewModel.setShowAddTemplateDialog(true) }) {
                            Icon(Icons.Default.Add, contentDescription = "添加模板")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isRecording) {
                RecordingPanel(
                    viewModel = viewModel,
                    templateName = recordingTemplateName,
                    isCountingDown = isCountingDown,
                    countdownSeconds = countdownSeconds,
                    progress = recordProgress,
                    targetFrames = recordTargetFrames,
                    isRecordingComplete = isRecordingComplete,
                    onStop = {
                        viewModel.stopRecording(
                            onSuccess = { /* template saved */ },
                            onFailure = { message ->
                                /* Show error message in snackbar or toast */
                            }
                        )
                    },
                    onCancel = { viewModel.cancelRecording() }
                )
            }

            if (templates.isEmpty() && !isRecording) {
                EmptyTemplateState()
            } else if (templates.isNotEmpty()) {
                TemplateList(
                    templates = templates,
                    onDelete = { viewModel.deleteTemplate(it) },
                    onRecord = { id, name -> viewModel.startRecordingForTemplate(id, name) },
                    enabled = !isRecording
                )
            }
        }

        if (showDialog) {
            AddTemplateDialog(
                name = newTemplateName,
                sensorType = selectedSensorType,
                sessionMode = selectedSessionMode,
                onNameChange = { viewModel.setNewTemplateName(it) },
                onSensorTypeChange = { viewModel.setSelectedSensorType(it) },
                onSessionModeChange = { viewModel.setSelectedSessionMode(it) },
                onConfirm = {
                    if (newTemplateName.isNotBlank()) {
                        viewModel.createTemplate(newTemplateName, selectedSensorType, selectedSessionMode)
                    }
                },
                onDismiss = { viewModel.setShowAddTemplateDialog(false) }
            )
        }
    }
}

@Composable
private fun RecordingPanel(
    viewModel: TemplateViewModel,
    templateName: String,
    isCountingDown: Boolean,
    countdownSeconds: Int,
    isRecordingComplete: Boolean,
    progress: Int,
    targetFrames: Int,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val keypoints by viewModel.keypoints.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    val isRecordingComplete by viewModel.isRecordingComplete.collectAsState()

    // Camera selection state
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val selectedCameraId by viewModel.selectedCameraId.collectAsState()
    val availableCameras by viewModel.availableCameras.collectAsState()
    // Use selected camera or let ViewModel handle default

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview with pose overlay (full screen)
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            PoseCameraPreview(
                cameraId = selectedCameraId ?: "0",
                onBitmap = { bitmap -> viewModel.processBitmap(bitmap) },
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
            // Recording / Countdown indicator
            if (isCountingDown) {
                CountdownOverlay(
                    seconds = countdownSeconds,
                    message = "请退后，全身入镜",
                    onCancel = onCancel
                )
            } else {
                if (isRecordingComplete) {
                    // Recording complete overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "录制完成",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(120.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "录制完成",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    color = Color(0xFF4CAF50),
                                    fontSize = 48.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "可以停止动作了",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White
                                )
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "录制中",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(32.dp)
                    )
                }
            }
        }

        // Bottom panel (overlay on preview)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                if (isCountingDown) {
                    Text(
                        text = "准备录制: $templateName",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "倒计时结束后自动开始录制",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onCancel) {
                        Text("取消")
                    }
                } else {
                    Text(
                        text = if (isRecordingComplete) "✓ 录制完成" else "正在录制: $templateName",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRecordingComplete) "可以停止动作了" else "帧数: $progress / $targetFrames",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (targetFrames > 0 && !isRecordingComplete) {
                        LinearProgressIndicator(
                            progress = (progress.toFloat() / targetFrames.toFloat()).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onStop) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("保存")
                        }
                        OutlinedButton(onClick = onCancel) {
                            Text("取消")
                        }
                    }
                }
            }
        }
    }

@Composable
private fun EmptyTemplateState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "暂无模板",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "点击右上角添加模板",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun TemplateList(
    templates: List<Template>,
    onDelete: (Long) -> Unit,
    onRecord: (Long, String) -> Unit,
    enabled: Boolean = true
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(templates) { template ->
            TemplateItem(
                template = template,
                onDelete = { onDelete(template.id) },
                onRecord = { onRecord(template.id, template.name) },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun TemplateItem(
    template: Template,
    onDelete: () -> Unit = {},
    onRecord: () -> Unit = {},
    enabled: Boolean = true
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .then(if (!enabled) Modifier.alpha(0.5f) else Modifier),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name + if (template.mode == SessionMode.TIMER) " (计时)" else "",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${if (template.type == TemplateType.BUILTIN) "内置" else "自定义"} · ${if (template.sensorType == SensorType.VISION) "视觉" else "音频"} · ${if (template.mode == SessionMode.TIMER) "计时" else "计数"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (template.featureVector != null) {
                    Text(
                        text = "已录制特征向量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row {
                // Record button for custom templates without feature vector
                if (template.type == TemplateType.CUSTOM && template.featureVector == null && enabled) {
                    IconButton(onClick = onRecord) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = "录制模板",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                // Delete button for custom templates
                if (template.type == TemplateType.CUSTOM && enabled) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除模板",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTemplateDialog(
    name: String,
    sensorType: SensorType,
    sessionMode: SessionMode,
    onNameChange: (String) -> Unit,
    onSensorTypeChange: (SensorType) -> Unit,
    onSessionModeChange: (SessionMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加模板") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("模板名称") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("传感器类型", style = MaterialTheme.typography.bodyMedium)
                Row {
                    SensorType.values().forEach { type ->
                        FilterChip(
                            selected = sensorType == type,
                            onClick = { onSensorTypeChange(type) },
                            label = { Text(if (type == SensorType.VISION) "视觉" else "音频") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("模式", style = MaterialTheme.typography.bodyMedium)
                Row {
                    SessionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = sessionMode == mode,
                            onClick = { onSessionModeChange(mode) },
                            label = { Text(if (mode == SessionMode.COUNTING) "计数" else "计时") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
