package com.snuabar.counter.ui.screen.counting

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.domain.model.ActionType

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

    // Camera2 state
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val surfaceReady = remember { mutableStateOf<SurfaceTexture?>(null) }
    val textureView = remember {
        TextureView(context).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    Log.d("Camera2", "SurfaceTexture available")
                    surfaceReady.value = surface
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    Log.d("Camera2", "SurfaceTexture destroyed")
                    surfaceReady.value = null
                    return true
                }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    // Determine effective camera ID (default to physical camera 2 = wide angle back)
    val effectiveCameraId = selectedCameraId ?: if (isFrontCamera) "1" else "2"

    // Track whether the actual open camera is front-facing (from hardware)
    var isActualFrontCamera by remember { mutableStateOf(false) }

    // Camera2 setup
    DisposableEffect(effectiveCameraId, hasCameraPermission, surfaceReady.value) {
        if (!hasCameraPermission || surfaceReady.value == null) return@DisposableEffect onDispose {}

        val surfaceTexture = surfaceReady.value!!
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null
        var isDisposed = false
        val cameraThread = android.os.HandlerThread("CameraThread").apply { start() }
        val cameraHandler = android.os.Handler(cameraThread.looper)
        val analysisThread = android.os.HandlerThread("AnalysisThread").apply { start() }
        val analysisHandler = android.os.Handler(analysisThread.looper)

        // ImageReader for detection (YUV_420_888 - no HAL encoding overhead)
        // Use higher resolution for better keypoint confidence
        val imageReader = ImageReader.newInstance(
            1280, 720,
            ImageFormat.YUV_420_888, 3
        )
        var lastProcessTime = 0L
        imageReader.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            // Throttle: max 10 fps for detection
            if (now - lastProcessTime < 100) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            lastProcessTime = now
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            // Convert YUV on camera thread (fast), then pass bitmap to analysis thread
            val bitmap = try {
                yuvToBitmap(image)
            } catch (e: Exception) {
                Log.e("Camera2", "YUV conversion error: ${e.message}")
                image.close()
                return@setOnImageAvailableListener
            }
            image.close()
            analysisHandler.post {
                try {
                    viewModel.processBitmap(bitmap)
                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.e("Camera2", "Frame processing error: ${e.message}")
                }
            }
        }, cameraHandler)

        var retryCount = 0

        fun openCameraAndStartPreview() {
            if (isDisposed) return
            try {
                val chars = cameraManager.getCameraCharacteristics(effectiveCameraId)
                val isFrontFacing = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                isActualFrontCamera = isFrontFacing
                val outputSizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(SurfaceTexture::class.java)
                val previewSize = outputSizes?.minByOrNull {
                    kotlin.math.abs(it.width * it.height - 1280 * 720)
                } ?: Size(1280, 720)

                Log.d("Camera2", "Opening camera $effectiveCameraId with preview ${previewSize.width}x${previewSize.height}")

                surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                val previewSurface = Surface(surfaceTexture)
                val analysisSurface = imageReader.surface

                cameraManager.openCamera(effectiveCameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (isDisposed) { camera.close(); return }
                        Log.d("Camera2", "Camera $effectiveCameraId opened successfully")
                        cameraDevice = camera
                        val surfaces = listOf(previewSurface, analysisSurface)

                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if (isDisposed) { session.close(); return }
                                captureSession = session
                                try {
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        addTarget(previewSurface)
                                        addTarget(analysisSurface)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                    }.build()
                                    session.setRepeatingRequest(request, null, cameraHandler)
                                    Log.d("Camera2", "Capture session configured for camera $effectiveCameraId")
                                    // Restart detection for new camera
                                    viewModel.setupDetection()
                                } catch (e: Exception) {
                                    Log.e("Camera2", "Failed to start capture: ${e.message}")
                                }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e("Camera2", "Capture session config failed for camera $effectiveCameraId")
                            }
                        }, cameraHandler)
                    }
                    override fun onDisconnected(camera: CameraDevice) {
                        Log.d("Camera2", "Camera $effectiveCameraId disconnected")
                        try { camera.close() } catch (e: Exception) {}
                        cameraDevice = null
                    }
                    override fun onError(camera: CameraDevice, error: Int) {
                        val errorStr = when (error) {
                            ERROR_CAMERA_DEVICE -> "DEVICE"
                            ERROR_CAMERA_DISABLED -> "DISABLED"
                            ERROR_CAMERA_IN_USE -> "IN_USE"
                            ERROR_MAX_CAMERAS_IN_USE -> "MAX_CAMERAS"
                            ERROR_CAMERA_SERVICE -> "SERVICE"
                            else -> "UNKNOWN($error)"
                        }
                        Log.e("Camera2", "Camera $effectiveCameraId error: $errorStr")
                        try { camera.close() } catch (e: Exception) {}
                        cameraDevice = null
                        if (error == ERROR_CAMERA_DISABLED && !isDisposed && retryCount < 1) {
                            retryCount++
                            Log.d("Camera2", "Retrying camera $effectiveCameraId in 1s (attempt $retryCount)...")
                            cameraHandler.postDelayed({ openCameraAndStartPreview() }, 1000)
                        }
                    }
                }, cameraHandler)
            } catch (e: SecurityException) {
                Log.e("Camera2", "Camera permission denied", e)
            } catch (e: Exception) {
                Log.e("Camera2", "Camera open failed: ${e.message}", e)
            }
        }

        openCameraAndStartPreview()

        onDispose {
            isDisposed = true
            viewModel.stopDetection()
            try {
                captureSession?.close()
                cameraDevice?.close()
                imageReader.close()
                cameraThread.quitSafely()
                analysisThread.quitSafely()
            } catch (e: Exception) {
                Log.d("Camera2", "Cleanup error: ${e.message}")
            }
        }
    }

    // Start detection when camera preview is ready (always running while preview is active)
    LaunchedEffect(surfaceReady.value) {
        if (surfaceReady.value != null) {
            viewModel.setupDetection()
        }
    }

    // Track preview box size for scaling
    var boxWidth by remember { mutableFloatStateOf(0f) }
    var boxHeight by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Camera preview (top portion)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    boxWidth = coordinates.size.width.toFloat()
                    boxHeight = coordinates.size.height.toFloat()
                },
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                // Calculate scale to fill the box (camera output is landscape, screen is portrait)
                val cameraAspect = 720f / 1280f  // landscape camera: width/height
                val boxAspect = if (boxHeight > 0f && boxWidth > 0f) boxWidth / boxHeight else 1f
                val fillScaleX = if (boxAspect < cameraAspect) cameraAspect / boxAspect else 1f
                val fillScaleY = if (boxAspect > cameraAspect) boxAspect / cameraAspect else 1f

                AndroidView(
                    factory = { textureView },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = fillScaleX,
                            scaleY = fillScaleY
                        )
                        .drawWithContent {
                            drawContent()
                            // Draw pose keypoints overlay
                            keypoints?.let { kps ->
                                val pointRadius = 6f
                                val lineWidth = 3f
                                val minConfidence = 0.3f

                                // COCO skeleton connections
                                val connections = listOf(
                                    0 to 1, 0 to 2, 1 to 3, 2 to 4,  // Head
                                    5 to 6, 5 to 7, 7 to 9, 6 to 8, 8 to 10, // Arms
                                    5 to 11, 6 to 12, 11 to 12, // Torso
                                    11 to 13, 13 to 15, 12 to 14, 14 to 16 // Legs
                                )

                                // Helper: convert model [y, x] to screen coords (camera is landscape, display is portrait)
                                fun toScreen(kp: FloatArray): Offset {
                                    return if (isActualFrontCamera) {
                                        // Front camera (sensor=270°): x-axis is inverted,
                                        // y-axis maps same as back camera
                                        Offset((1f - kp[0]) * size.width, (1f - kp[1]) * size.height)
                                    } else {
                                        // Back camera (sensor=90°): normal landscape-to-portrait
                                        Offset((1f - kp[0]) * size.width, kp[1] * size.height)
                                    }
                                }

                                // Draw skeleton lines
                                connections.forEach { (i, j) ->
                                    if (kps[i][2] > minConfidence && kps[j][2] > minConfidence) {
                                        drawLine(
                                            color = Color(0xFF00FF00),
                                            start = toScreen(kps[i]),
                                            end = toScreen(kps[j]),
                                            strokeWidth = lineWidth
                                        )
                                    }
                                }

                                // Draw keypoints
                                kps.forEach { kp ->
                                    if (kp[2] > minConfidence) {
                                        drawCircle(
                                            color = Color(0xFFFF0000),
                                            radius = pointRadius,
                                            center = toScreen(kp)
                                        )
                                    }
                                }
                            }

                            // Draw detection status indicator (always when detection is active)
                            if (keypoints != null) {
                                val hasPerson = keypoints?.any { it[2] > 0.5f } ?: false
                                drawCircle(
                                    color = if (hasPerson) Color(0xFF00FF00) else Color(0xFFFF0000),
                                    radius = 12f,
                                    center = Offset(40f, size.height - 40f)
                                )
                            }
                        }
                )
                // Camera selection dropdown
                if (availableCameras.size > 1) {
                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.width(140.dp)
                        ) {
                            Text(
                                text = selectedCameraId?.let { availableCameras[it] ?: "摄像头 $it" } 
                                    ?: if (isFrontCamera) "默认前置" else "默认后置",
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableCameras.forEach { (cameraId, displayName) ->
                                DropdownMenuItem(
                                    text = { Text(displayName) },
                                    onClick = {
                                        viewModel.selectCamera(cameraId)
                                        expanded = false
                                    }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = { Text(if (isFrontCamera) "默认前置" else "默认后置") },
                                onClick = {
                                    viewModel.selectCamera(null)
                                    expanded = false
                                }
                            )
                        }
                    }
                } else {
                    // Single camera - just show toggle button
                    IconButton(
                        onClick = { viewModel.toggleCamera() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "切换摄像头",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
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
                val actionName = when (actionType) {
                    ActionType.PUSH_UP -> "俯卧撑"
                    ActionType.SQUAT -> "深蹲"
                    ActionType.CUSTOM -> selectedTemplate?.name ?: "自定义"
                }
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
        }
    }
}

/**
 * Fast YUV_420_888 to Bitmap conversion (direct math, no JPEG)
 */
private fun yuvToBitmap(image: android.media.Image): Bitmap {
    val width = image.width
    val height = image.height
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val yRowStride = yPlane.rowStride
    val uvRowStride = uPlane.rowStride
    val uvPixelStride = uPlane.pixelStride

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)

    for (row in 0 until height) {
        val yRowOffset = row * yRowStride
        val uvRowOffset = (row / 2) * uvRowStride
        for (col in 0 until width) {
            val yIdx = yRowOffset + col
            val uvIdx = uvRowOffset + (col / 2) * uvPixelStride

            val y = (yBuffer.get(yIdx).toInt() and 0xFF) - 16
            val u = (uBuffer.get(uvIdx).toInt() and 0xFF) - 128
            val v = (vBuffer.get(uvIdx).toInt() and 0xFF) - 128

            val yy = y.coerceAtLeast(0)
            var r = (1.164f * yy + 1.596f * v).toInt()
            var g = (1.164f * yy - 0.813f * v - 0.391f * u).toInt()
            var b = (1.164f * yy + 2.018f * u).toInt()

            r = r.coerceIn(0, 255)
            g = g.coerceIn(0, 255)
            b = b.coerceIn(0, 255)

            pixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}
