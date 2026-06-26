package com.snuabar.counter.ui.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * Unified camera preview component with pose skeleton overlay and camera switching.
 * Both CountingScreen and TemplateScreen use this component for consistent preview behavior.
 */
@Composable
fun PoseCameraPreview(
    cameraId: String,
    onBitmap: (Bitmap) -> Unit,
    onCameraReady: (() -> Unit)? = null,
    onCameraDisposed: (() -> Unit)? = null,
    keypoints: Array<FloatArray>?,
    // Camera switching
    showCameraSwitch: Boolean = false,
    availableCameras: Map<String, String> = emptyMap(),
    selectedCameraId: String? = null,
    isFrontCamera: Boolean = false,
    onCameraSwitch: ((String?) -> Unit)? = null,
    onToggleCamera: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Camera permission state
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

    // Determine if actual camera is front-facing for skeleton mirroring
    var isActualFrontCamera by remember { mutableStateOf(false) }
    LaunchedEffect(cameraId) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            isActualFrontCamera = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } catch (e: Exception) {
            Log.e("PoseCameraPreview", "Failed to get camera characteristics", e)
        }
    }

    // Track preview box size for scaling
    var boxWidth by remember { mutableFloatStateOf(0f) }
    var boxHeight by remember { mutableFloatStateOf(0f) }

    // Calculate scale to fill the box (camera output is landscape, screen is portrait)
    val cameraAspect = 720f / 1280f
    val boxAspect = if (boxHeight > 0f && boxWidth > 0f) boxWidth / boxHeight else 1f
    val fillScaleX = if (boxAspect < cameraAspect) cameraAspect / boxAspect else 1f
    val fillScaleY = if (boxAspect > cameraAspect) boxAspect / cameraAspect else 1f

    if (hasCameraPermission) {
        Box(modifier = modifier) {
            Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    boxWidth = coordinates.size.width.toFloat()
                    boxHeight = coordinates.size.height.toFloat()
                },
            contentAlignment = Alignment.Center
        ) {
            Camera2Preview(
                cameraId = cameraId,
                onBitmap = onBitmap,
                onCameraReady = onCameraReady,
                onCameraDisposed = onCameraDisposed,
                modifier = Modifier.fillMaxSize()
            ) { textureView ->
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
                                    0 to 1, 0 to 2, 1 to 3, 2 to 4,
                                    5 to 6, 5 to 7, 7 to 9, 6 to 8, 8 to 10,
                                    5 to 11, 6 to 12, 11 to 12,
                                    11 to 13, 13 to 15, 12 to 14, 14 to 16
                                )

                                fun toScreen(kp: FloatArray): Offset {
                                    val x = if (isActualFrontCamera) 1f - kp[0] else 1f - kp[0]
                                    val y = if (isActualFrontCamera) 1f - kp[1] else kp[1]
                                    return Offset(x * size.width, y * size.height)
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

                                // Draw detection status indicator
                                val hasPerson = kps.any { it[2] > 0.5f }
                                drawCircle(
                                    color = if (hasPerson) Color(0xFF00FF00) else Color(0xFFFF0000),
                                    radius = 12f,
                                    center = Offset(40f, size.height - 40f)
                                )
                            }
                        }
                )
            }
        }

        // Camera switch UI
        if (showCameraSwitch) {
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
                        availableCameras.forEach { (id, displayName) ->
                            DropdownMenuItem(
                                text = { Text(displayName) },
                                onClick = {
                                    onCameraSwitch?.invoke(id)
                                    expanded = false
                                }
                            )
                        }
                        Divider()
                        DropdownMenuItem(
                            text = { Text(if (isFrontCamera) "默认前置" else "默认后置") },
                            onClick = {
                                onCameraSwitch?.invoke(null)
                                expanded = false
                            }
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = { onToggleCamera?.invoke() },
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
        }
    }
} else {
        Column(
            modifier = modifier.fillMaxSize(),
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
