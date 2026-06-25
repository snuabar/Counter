package com.snuabar.counter.ui.screen.template

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.SessionMode
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    viewModel: TemplateViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsState()
    val showDialog by viewModel.showAddTemplateDialog.collectAsState()
    val newTemplateName by viewModel.newTemplateName.collectAsState()
    val selectedSensorType by viewModel.selectedSensorType.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordProgress by viewModel.recordProgress.collectAsState()
    val recordTargetFrames by viewModel.recordTargetFrames.collectAsState()
    val recordingTemplateName by viewModel.recordingTemplateName.collectAsState()

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
                    progress = recordProgress,
                    targetFrames = recordTargetFrames,
                    onStop = {
                        viewModel.stopRecording(
                            onSuccess = { /* template saved */ },
                            onFailure = { /* no data collected */ }
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
                onNameChange = { viewModel.setNewTemplateName(it) },
                onSensorTypeChange = { viewModel.setSelectedSensorType(it) },
                onConfirm = {
                    if (newTemplateName.isNotBlank()) {
                        viewModel.createTemplate(newTemplateName, selectedSensorType)
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
    progress: Int,
    targetFrames: Int,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keypoints by viewModel.keypoints.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_START
        }
    }
    val cameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider.value = future.get()
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(lifecycleOwner, isRecording, cameraProvider.value) {
        val provider = cameraProvider.value
        if (provider == null) return@DisposableEffect onDispose {}

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }

        val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        viewModel.getImageAnalyzer()?.let { analyzer ->
            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
        }
        useCases.add(imageAnalysis)

        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            *useCases.toTypedArray()
        )

        onDispose {
            provider.unbindAll()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Camera preview with pose overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        // Draw pose keypoints
                        keypoints?.let { kps ->
                            val pointRadius = 6f
                            val lineWidth = 3f
                            val minConfidence = 0.3f
                            val connections = listOf(
                                0 to 1, 0 to 2, 1 to 3, 2 to 4,
                                5 to 6, 5 to 7, 7 to 9, 6 to 8, 8 to 10,
                                5 to 11, 6 to 12, 11 to 12,
                                11 to 13, 13 to 15, 12 to 14, 14 to 16
                            )
                            connections.forEach { (i, j) ->
                                if (kps[i][2] > minConfidence && kps[j][2] > minConfidence) {
                                    drawLine(
                                        color = Color(0xFF00FF00),
                                        start = Offset(kps[i][1] * size.width, kps[i][0] * size.height),
                                        end = Offset(kps[j][1] * size.width, kps[j][0] * size.height),
                                        strokeWidth = lineWidth
                                    )
                                }
                            }
                            kps.forEach { kp ->
                                if (kp[2] > minConfidence) {
                                    drawCircle(
                                        color = Color(0xFFFF0000),
                                        radius = pointRadius,
                                        center = Offset(kp[1] * size.width, kp[0] * size.height)
                                    )
                                }
                            }
                            val hasPerson = kps.any { it[2] > 0.5f }
                            drawCircle(
                                color = if (hasPerson) Color(0xFF00FF00) else Color(0xFFFF0000),
                                radius = 12f,
                                center = Offset(40f, size.height - 40f)
                            )
                        }
                    }
            )
            // Recording indicator
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

        // Bottom panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
    onNameChange: (String) -> Unit,
    onSensorTypeChange: (SensorType) -> Unit,
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
