package com.snuabar.counter.ui.screen.counting

import android.Manifest
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.core.detection.tflite.action.ActionType

import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTypeSelector(
    selectedAction: ActionType,
    onActionSelected: (ActionType) -> Unit,
    enabled: Boolean = true
) {
    val actions = listOf(
        ActionType.PUSH_UP to "俯卧撑",
        ActionType.SQUAT to "深蹲",
        ActionType.PLANK to "平板支撑"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        actions.forEach { (action, label) ->
            val isSelected = selectedAction == action
            FilterChip(
                selected = isSelected,
                onClick = { if (enabled) onActionSelected(action) },
                label = { Text(label) },
                enabled = enabled
            )
        }
    }
}

@Composable
fun CountingScreen(
    viewModel: CountingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentCount by viewModel.currentCount.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val confidence by viewModel.confidence.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isTimerMode by viewModel.isTimerMode.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val targetSeconds by viewModel.targetSeconds.collectAsState()
    val targetResolution by viewModel.targetResolution.collectAsState()
    val actionType by viewModel.actionType.collectAsState()

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

    // Camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // PreviewView
    val previewView = remember { PreviewView(context) }

    // Camera provider
    val cameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider.value = future.get()
        }, ContextCompat.getMainExecutor(context))
    }

    // Camera setup - always bind preview, add imageAnalysis when running
    DisposableEffect(lifecycleOwner, isRunning, hasCameraPermission, cameraProvider.value) {
        val provider = cameraProvider.value
        if (provider == null || !hasCameraPermission) return@DisposableEffect onDispose {}

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }

        val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)

        if (isRunning) {
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(targetResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            viewModel.getImageAnalyzer()?.let { analyzer ->
                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
            }

            useCases.add(imageAnalysis)
        }

        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            *useCases.toTypedArray()
        )

        onDispose {
            provider.unbindAll()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Camera preview (top portion)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("需要相机权限")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("授予权限")
                    }
                }
            }
        }

        // Bottom UI section
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                ActionTypeSelector(
                    selectedAction = actionType,
                    onActionSelected = { viewModel.setActionType(it) },
                    enabled = !isRunning
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Show current action during counting
                val actionName = when (actionType) {
                    ActionType.PUSH_UP -> "俯卧撑"
                    ActionType.SQUAT -> "深蹲"
                    ActionType.PLANK -> "平板支撑"
                    ActionType.CUSTOM -> "自定义"
                }
                Text(
                    text = "当前动作: $actionName",
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
                    Button(onClick = { viewModel.startCounting(mode = com.snuabar.counter.domain.model.SessionMode.COUNTING, actionType = actionType) }) {
                        Text("开始计数")
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
