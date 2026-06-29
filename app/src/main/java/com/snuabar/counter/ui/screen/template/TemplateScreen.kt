package com.snuabar.counter.ui.screen.template

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.SessionMode
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType
import com.snuabar.counter.ui.component.CountdownOverlay
import com.snuabar.counter.ui.component.PoseCameraPreview
import com.snuabar.counter.ui.component.SkeletonAnimationPreview
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    viewModel: TemplateViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsState()
    val showDialog by viewModel.showAddTemplateDialog.collectAsState()
    val showEditDialog by viewModel.showEditTemplateDialog.collectAsState()
    val editingTemplateId by viewModel.editingTemplateId.collectAsState()
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

    val context = LocalContext.current

    // Expanded template ID for swipe-to-delete
    var expandedTemplateId by remember { mutableStateOf<Long?>(null) }

    val listState = rememberLazyListState()

    // Auto collapse swipe-to-delete when scrolling
    if (listState.isScrollInProgress && expandedTemplateId != null) {
        expandedTemplateId = null
    }

    // Auto collapse when recording starts
    LaunchedEffect(isRecording) {
        if (isRecording) {
            expandedTemplateId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模板管理") },
                actions = {
                    if (!isRecording) {
                        IconButton(onClick = {
                            expandedTemplateId = null
                            viewModel.setShowAddTemplateDialog(true)
                        }) {
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
                            onSuccess = {
                                // Recording complete, overlay will show success state
                            },
                            onFailure = { message ->
                                if (!message.startsWith("QUALITY_LOW:")) {
                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                                }
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
                    expandedTemplateId = expandedTemplateId,
                    onExpandChanged = { expandedTemplateId = it },
                    onClickTemplate = { viewModel.startEditTemplate(it) },
                    onDelete = { viewModel.deleteTemplate(it) },
                    enabled = !isRecording,
                    listState = listState
                )
            }
        }

        if (showDialog) {
            AddTemplateDialog(
                title = "添加模板",
                confirmText = "添加",
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

        val editingTemplate = templates.find { it.id == editingTemplateId }
        if (showEditDialog && editingTemplate != null) {
            TemplateEditDialog(
                template = editingTemplate,
                editingName = newTemplateName,
                onNameChange = { viewModel.setNewTemplateName(it) },
                onReRecord = {
                    viewModel.cancelEditTemplate()
                    viewModel.startRecordingForTemplate(editingTemplate.id, editingTemplate.name)
                },
                onSave = {
                    editingTemplateId?.let { id ->
                        viewModel.updateTemplateName(id, newTemplateName)
                    }
                    viewModel.cancelEditTemplate()
                },
                onDismiss = { viewModel.cancelEditTemplate() }
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
    val isPreviewMode by viewModel.isPreviewMode.collectAsState()
    val previewKeypointSequence by viewModel.previewKeypointSequence.collectAsState()
    val segmentStart by viewModel.segmentStart.collectAsState()
    val segmentEnd by viewModel.segmentEnd.collectAsState()
    val computedScore by viewModel.computedScore.collectAsState()
    val totalFrames by viewModel.totalRecordedFrames.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val selectedCameraId by viewModel.selectedCameraId.collectAsState()
    val availableCameras by viewModel.availableCameras.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isPreviewMode) {
            // Preview mode: skeleton animation + slider + score + buttons
            Column(modifier = Modifier.fillMaxSize()) {
                // Skeleton preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    SkeletonAnimationPreview(
                        keypointSequence = previewKeypointSequence,
                        modifier = Modifier.fillMaxSize(),
                        frameDelayMs = 100
                    )
                }

                // Score and controls panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isPassing = computedScore >= 50f
                    val scoreColor = if (isPassing) MaterialTheme.colorScheme.primary else Color(0xFFF44336)

                    // Score
                    Text(
                        text = "评分: ${String.format("%.0f", computedScore)}分",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = scoreColor,
                            fontSize = 32.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Range slider
                    if (totalFrames > 0) {
                        RangeSlider(
                            value = segmentStart.toFloat()..segmentEnd.toFloat(),
                            onValueChange = { range ->
                                val newStart = (range.start + 0.5f).toInt().coerceIn(0, totalFrames)
                                val newEnd = (range.endInclusive + 0.5f).toInt().coerceIn(0, totalFrames)
                                if (newStart < newEnd) {
                                    viewModel.updateSegmentRange(newStart, newEnd)
                                }
                            },
                            valueRange = 0f..totalFrames.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "帧范围: $segmentStart - $segmentEnd (共${segmentEnd - segmentStart}帧)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isPassing) {
                            Button(onClick = {
                                viewModel.savePreviewTemplate(
                                    onSuccess = {},
                                    onFailure = { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show() }
                                )
                            }) {
                                Text("保存")
                            }
                        } else {
                            Button(onClick = {
                                viewModel.cancelRecording()
                                viewModel.startRecording(templateName)
                            }) {
                                Text("重录")
                            }
                        }
                        OutlinedButton(onClick = onCancel) {
                            Text("取消")
                        }
                    }
                }
            }
        } else {
            // Recording mode: camera preview + controls
            Box(modifier = Modifier.fillMaxSize()) {
                // Camera preview with pose overlay (full screen)
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

                // Auto-stop when target is reached
                if (!isCountingDown) {
                    LaunchedEffect(isRecordingComplete) {
                        if (isRecordingComplete) {
                            onStop()
                        }
                    }
                }

                // Recording / Countdown indicator
                if (isCountingDown) {
                    CountdownOverlay(
                        seconds = countdownSeconds,
                        message = "请退后，全身入镜",
                        onCancel = onCancel
                    )
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
                        text = "正在录制: $templateName",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "帧数: $progress / $targetFrames",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (targetFrames > 0) {
                        LinearProgressIndicator(
                            progress = (progress.toFloat() / targetFrames.toFloat()).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
    expandedTemplateId: Long?,
    onExpandChanged: (Long?) -> Unit,
    onClickTemplate: (Template) -> Unit,
    onDelete: (Long) -> Unit,
    enabled: Boolean = true,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(templates) { template ->
            TemplateItem(
                template = template,
                isExpanded = expandedTemplateId == template.id,
                onExpandChanged = { expanded ->
                    onExpandChanged(if (expanded) template.id else null)
                },
                onClick = { onClickTemplate(template) },
                onDelete = { onDelete(template.id) },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun TemplateItem(
    template: Template,
    isExpanded: Boolean,
    onExpandChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean = true
) {
    val isCustom = template.type == TemplateType.CUSTOM
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val maxSwipePx = with(LocalDensity.current) { 80.dp.toPx() }

    LaunchedEffect(isExpanded) {
        val target = if (isExpanded) -maxSwipePx else 0f
        if (kotlin.math.abs(offsetX.value - target) > 1f) {
            offsetX.animateTo(target)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Background layer (red delete area) - only for custom templates
        if (isCustom) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.error, shape = MaterialTheme.shapes.medium),
                contentAlignment = Alignment.CenterEnd
            ) {
                IconButton(
                    onClick = {
                        onExpandChanged(false)
                        onDelete()
                    },
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }

        // Foreground layer (card content)
        val cardModifier = if (isCustom) {
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -maxSwipePx / 2) {
                                    offsetX.animateTo(-maxSwipePx)
                                    onExpandChanged(true)
                                } else {
                                    offsetX.animateTo(0f)
                                    onExpandChanged(false)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = (offsetX.value + dragAmount).coerceIn(-maxSwipePx, 0f)
                            scope.launch { offsetX.snapTo(newOffset) }
                        }
                    )
                }
        } else {
            Modifier.fillMaxWidth()
        }

        Card(
            modifier = cardModifier
                .clickable {
                    if (isExpanded) {
                        onExpandChanged(false)
                    } else {
                        onClick()
                    }
                }
                .then(if (!enabled) Modifier.alpha(0.5f) else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTemplateDialog(
    title: String,
    confirmText: String,
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
        title = { Text(title) },
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
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditDialog(
    template: Template,
    editingName: String,
    onNameChange: (String) -> Unit,
    onReRecord: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑模板") },
        text = {
            Column {
                // Skeleton animation preview
                if (template.keypointSequence != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color.Black, shape = MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        SkeletonAnimationPreview(
                            keypointSequence = template.keypointSequence,
                            modifier = Modifier.fillMaxSize(),
                            frameDelayMs = 100
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Name field (editable)
                OutlinedTextField(
                    value = editingName,
                    onValueChange = onNameChange,
                    label = { Text("模板名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Sensor type (read-only)
                Text(
                    text = "传感器: ${if (template.sensorType == SensorType.VISION) "视觉" else "音频"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                // Mode (read-only)
                Text(
                    text = "模式: ${if (template.mode == SessionMode.TIMER) "计时" else "计数"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Re-record button
                OutlinedButton(
                    onClick = onReRecord,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新录制")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
