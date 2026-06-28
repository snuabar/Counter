package com.snuabar.counter.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Shared Camera2 preview component.
 * Manages all Camera2 lifecycle: TextureView, ImageReader, camera open/close, threads.
 *
 * Usage:
 * ```
 * Camera2Preview(
 *     cameraId = "0",
 *     onBitmap = { bitmap -> viewModel.processBitmap(bitmap) },
 *     onCameraReady = { viewModel.setupDetection() },
 *     onDispose = { viewModel.stopDetection() }
 * ) { textureView ->
 *     AndroidView(
 *         factory = { textureView },
 *         modifier = Modifier.fillMaxSize()
 *     )
 * }
 * ```
 */
@Composable
fun Camera2Preview(
    cameraId: String,
    onBitmap: (Bitmap) -> Unit,
    onCameraReady: (() -> Unit)? = null,
    onCameraDisposed: (() -> Unit)? = null,
    isFrontCamera: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable (TextureView) -> Unit
) {
    val context = LocalContext.current
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val surfaceReady = remember { mutableStateOf<SurfaceTexture?>(null) }

    val textureView = remember {
        TextureView(context).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    Log.d("Camera2Preview", "SurfaceTexture available")
                    surfaceReady.value = surface
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    Log.d("Camera2Preview", "SurfaceTexture destroyed")
                    surfaceReady.value = null
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    DisposableEffect(cameraId, surfaceReady.value) {
        if (surfaceReady.value == null) return@DisposableEffect onDispose {}

        val surfaceTexture = surfaceReady.value!!
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null
        var isDisposed = false
        val cameraThread = HandlerThread("CameraThread").apply { start() }
        val cameraHandler = Handler(cameraThread.looper)

        // Get preview size first to match ImageReader with SurfaceTexture
        val previewSize = try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val outputSizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
            // Prefer landscape sizes to ensure consistent rotation logic
            val landscapeSizes = outputSizes?.filter { it.width > it.height }
            if (landscapeSizes != null && landscapeSizes.isNotEmpty()) {
                landscapeSizes.minByOrNull {
                    kotlin.math.abs(it.width * it.height - 640 * 360)
                } ?: Size(640, 360)
            } else {
                outputSizes?.firstOrNull() ?: Size(640, 360)
            }
        } catch (e: Exception) {
            Size(640, 360)
        }

        // ImageReader for detection (YUV_420_888) - match preview size
        val imageReader = ImageReader.newInstance(
            previewSize.width, previewSize.height,
            ImageFormat.YUV_420_888, 3
        )
        var lastProcessTime = 0L
        imageReader.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            // Throttle: fixed at 30 fps
            if (now - lastProcessTime < 33) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            lastProcessTime = now
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = yuvToBitmap(image)
                image.close()
                // Rotate landscape bitmap to portrait for MediaPipe
                val rotatedBitmap = if (bitmap.width > bitmap.height) {
                    val localCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val chars = try { localCameraManager.getCameraCharacteristics(cameraId) } catch (_: Exception) { null }
                    val sensorOrientation = chars?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                    val rotationAngle = sensorOrientation.toFloat()
                    Log.d("Camera2Preview", "Rotating cameraId=$cameraId bitmap=${bitmap.width}x${bitmap.height} sensorOrientation=$sensorOrientation rotation=$rotationAngle")
                    val matrix = android.graphics.Matrix().apply { postRotate(rotationAngle) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                } else {
                    bitmap
                }
                onBitmap(rotatedBitmap)
            } catch (e: Exception) {
                Log.e("Camera2Preview", "Frame processing error: ${e.message}")
                try { image.close() } catch (_: Exception) {}
            }
        }, cameraHandler)

        fun openCamera() {
            if (isDisposed) return
            try {
                Log.d("Camera2Preview", "Opening camera $cameraId with preview ${previewSize.width}x${previewSize.height}")

                surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                val previewSurface = Surface(surfaceTexture)
                val analysisSurface = imageReader.surface

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (isDisposed) {
                            camera.close()
                            return
                        }
                        cameraDevice = camera
                        val surfaces = listOf(previewSurface, analysisSurface)

                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if (isDisposed) {
                                    session.close()
                                    return
                                }
                                captureSession = session
                                try {
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        addTarget(previewSurface)
                                        addTarget(analysisSurface)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                    }.build()
                                    session.setRepeatingRequest(request, null, cameraHandler)
                                    Log.d("Camera2Preview", "Capture session configured for camera $cameraId")
                                    // Notify caller that camera is ready
                                    onCameraReady?.invoke()
                                } catch (e: Exception) {
                                    Log.e("Camera2Preview", "Failed to start capture: ${e.message}")
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e("Camera2Preview", "Capture session config failed for camera $cameraId")
                            }
                        }, cameraHandler)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.d("Camera2Preview", "Camera $cameraId disconnected")
                        try {
                            camera.close()
                        } catch (e: Exception) {
                        }
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
                        Log.e("Camera2Preview", "Camera $cameraId error: $errorStr")
                        try {
                            camera.close()
                        } catch (e: Exception) {
                        }
                        cameraDevice = null
                    }
                }, cameraHandler)
            } catch (e: SecurityException) {
                Log.e("Camera2Preview", "Camera permission denied", e)
            } catch (e: Exception) {
                Log.e("Camera2Preview", "Camera open failed: ${e.message}", e)
            }
        }

        openCamera()

        onDispose {
            isDisposed = true
            onCameraDisposed?.invoke()
            try {
                captureSession?.close()
                cameraDevice?.close()
                imageReader.close()
                cameraThread.quitSafely()
            } catch (e: Exception) {
                Log.d("Camera2Preview", "Cleanup error: ${e.message}")
            }
        }
    }

    content(textureView)
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

    // Copy buffers to arrays for fast indexed access (avoid repeated JNI calls)
    val yBytes = ByteArray(yBuffer.remaining())
    val uBytes = ByteArray(uBuffer.remaining())
    val vBytes = ByteArray(vBuffer.remaining())
    yBuffer.get(yBytes)
    uBuffer.get(uBytes)
    vBuffer.get(vBytes)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)

    for (row in 0 until height) {
        val yRowOffset = row * yRowStride
        val uvRowOffset = (row / 2) * uvRowStride
        for (col in 0 until width) {
            val yIdx = yRowOffset + col
            val uvIdx = uvRowOffset + (col / 2) * uvPixelStride

            val y = (yBytes[yIdx].toInt() and 0xFF) - 16
            val u = (uBytes[uvIdx].toInt() and 0xFF) - 128
            val v = (vBytes[uvIdx].toInt() and 0xFF) - 128

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
