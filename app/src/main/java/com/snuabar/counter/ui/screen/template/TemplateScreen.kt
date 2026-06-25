package com.snuabar.counter.ui.screen.template

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.SessionMode
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    viewModel: TemplateViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsState()
    val showDialog by viewModel.showAddTemplateDialog.collectAsState()
    val newTemplateName by viewModel.newTemplateName.collectAsState()
    val selectedSensorType by viewModel.selectedSensorType.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模板管理") },
                actions = {
                    IconButton(onClick = { viewModel.setShowAddTemplateDialog(true) }) {
                        Icon(Icons.Default.Add, contentDescription = "添加模板")
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
            if (templates.isEmpty()) {
                EmptyTemplateState()
            } else {
                TemplateList(
                    templates = templates,
                    onDelete = { viewModel.deleteTemplate(it) }
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
    onDelete: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(templates) { template ->
            TemplateItem(
                template = template,
                onDelete = { onDelete(template.id) }
            )
        }
    }
}

@Composable
private fun TemplateItem(
    template: Template,
    onDelete: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
            }
            if (template.type == TemplateType.CUSTOM) {
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
