# MediaPipe Pose Landmarker Android 集成指南

> 本文档面向 AI 助手和开发者，详细说明如何在 Android 工程中集成 MediaPipe Pose Landmarker。

---

## 目录

1. [项目概述](#1-项目概述)
2. [环境要求](#2-环境要求)
3. [Gradle 依赖配置](#3-gradle-依赖配置)
4. [模型文件获取](#4-模型文件获取)
5. [核心封装类 PoseLandmarkerHelper](#5-核心封装类-poselandmarkerhelper)
6. [三种使用模式详解](#6-三种使用模式详解)
7. [检测结果解析](#7-检测结果解析)
8. [UI 绘制（可选）](#8-ui-绘制可选)
9. [常见问题与注意事项](#9-常见问题与注意事项)

---

## 1. 项目概述

MediaPipe Pose Landmarker 是 Google 提供的人体姿态关键点检测解决方案。该 Android 示例项目展示了如何从以下三种输入源进行姿态检测：

- **单张图片（IMAGE）**：对静态图片进行一次性检测
- **视频文件（VIDEO）**：逐帧分析视频内容
- **实时相机流（LIVE_STREAM）**：通过 CameraX 实时捕获并检测

检测输出包含 **33 个人体关键点**，覆盖全身主要关节和面部特征点。

---

## 2. 环境要求

| 项目 | 要求 |
|------|------|
| Android Studio | Dolphin 或更高版本 |
| compileSdk | 34 |
| minSdk | 24 (Android 7.0) |
| targetSdk | 34 |
| 设备 | 建议使用物理设备（带摄像头） |
| Kotlin | 1.8.0+ |
| Java 兼容性 | VERSION_1_8 |

---

## 3. Gradle 依赖配置

### 3.1 项目级 build.gradle

```gradle
buildscript {
    dependencies {
        classpath 'com.android.tools.build:gradle:8.11.0'
    }
}
plugins {
    id 'com.android.application' version '7.4.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.8.0' apply false
    id 'de.undercouch.download' version '4.1.2' apply false
}
```

### 3.2 模块级 build.gradle (app/build.gradle)

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'de.undercouch.download'
}

android {
    namespace 'com.yourpackage.yourapp'
    compileSdk 34

    defaultConfig {
        applicationId "com.yourpackage.yourapp"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = '1.8'
    }
    buildFeatures {
        viewBinding true
    }
}

dependencies {
    // Kotlin
    implementation 'androidx.core:core-ktx:1.8.0'
    implementation 'androidx.appcompat:appcompat:1.5.1'
    
    // MediaPipe Tasks Vision - 核心依赖
    implementation 'com.google.mediapipe:tasks-vision:0.10.29'
    
    // 可选：如果需要 CameraX 实时流
    def camerax_version = '1.4.2'
    implementation "androidx.camera:camera-core:$camerax_version"
    implementation "androidx.camera:camera-camera2:$camerax_version"
    implementation "androidx.camera:camera-lifecycle:$camerax_version"
    implementation "androidx.camera:camera-view:$camerax_version"
}
```

---

## 4. 模型文件获取

Pose Landmarker 需要 `.task` 格式的模型文件。有三种规格：

| 模型 | 文件名 | 特点 | 适用场景 |
|------|--------|------|----------|
| **Lite** | `pose_landmarker_lite.task` | 速度最快，精度较低 | 低端设备、实时性要求高 |
| **Full** | `pose_landmarker_full.task` | 速度与精度平衡 | 通用场景（推荐） |
| **Heavy** | `pose_landmarker_heavy.task` | 精度最高，速度最慢 | 精度优先 |

### 4.1 自动下载方式（Gradle Task）

创建 `app/download_tasks.gradle`：

```gradle
task downloadTaskFile(type: Download) {
    src 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task'
    dest projectDir.toString() + '/src/main/assets/pose_landmarker_full.task'
    overwrite false
}

task downloadTaskFile1(type: Download) {
    src 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task'
    dest projectDir.toString() + '/src/main/assets/pose_landmarker_lite.task'
    overwrite false
}

task downloadTaskFile2(type: Download) {
    src 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/1/pose_landmarker_heavy.task'
    dest projectDir.toString() + '/src/main/assets/pose_landmarker_heavy.task'
    overwrite false
}

preBuild.dependsOn downloadTaskFile, downloadTaskFile1, downloadTaskFile2
```

在 `app/build.gradle` 中引用：

```gradle
project.ext.ASSET_DIR = projectDir.toString() + '/src/main/assets'
apply from: 'download_tasks.gradle'
```

### 4.2 手动下载方式

从以下 URL 下载 `.task` 文件，放置到 `app/src/main/assets/` 目录：

- Full: `https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task`
- Lite: `https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task`
- Heavy: `https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/1/pose_landmarker_heavy.task`

---

## 5. 核心封装类 PoseLandmarkerHelper

### 5.1 设计理念

`PoseLandmarkerHelper` 是对 MediaPipe `PoseLandmarker` API 的封装，目的是：

- 简化模型初始化配置（CPU/GPU 选择、模型选择、置信度阈值）
- 统一三种检测模式（IMAGE/VIDEO/LIVE_STREAM）的调用接口
- 提供回调机制处理实时检测结果
- 自动管理资源释放

### 5.2 完整代码

```kotlin
package com.yourpackage.yourapp

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Pose Landmarker 封装类
 * 
 * 构造函数参数说明：
 * - minPoseDetectionConfidence: 姿态检测置信度阈值（默认 0.5）
 * - minPoseTrackingConfidence: 姿态跟踪置信度阈值（默认 0.5）
 * - minPosePresenceConfidence: 姿态存在置信度阈值（默认 0.5）
 * - currentModel: 模型选择（FULL/LITE/HEAVY）
 * - currentDelegate: 运行设备（CPU/GPU）
 * - runningMode: 运行模式（IMAGE/VIDEO/LIVE_STREAM）
 * - context: Android Context
 * - poseLandmarkerHelperListener: 结果回调监听器（LIVE_STREAM 模式必需）
 */
class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val poseLandmarkerHelperListener: LandmarkerListener? = null
) {

    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    /** 释放 PoseLandmarker 资源 */
    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    /** 检查是否已释放 */
    fun isClose(): Boolean = poseLandmarker == null

    /**
     * 初始化 PoseLandmarker
     * 
     * 注意：
     * - CPU Delegate 可以在主线程创建、后台线程使用
     * - GPU Delegate 必须在初始化线程上使用
     */
    fun setupPoseLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()

        // 选择运行设备
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
        }

        // 选择模型文件
        val modelName = when (currentModel) {
            MODEL_POSE_LANDMARKER_FULL -> "pose_landmarker_full.task"
            MODEL_POSE_LANDMARKER_LITE -> "pose_landmarker_lite.task"
            MODEL_POSE_LANDMARKER_HEAVY -> "pose_landmarker_heavy.task"
            else -> "pose_landmarker_full.task"
        }
        baseOptionBuilder.setModelAssetPath(modelName)

        // LIVE_STREAM 模式必须设置监听器
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (poseLandmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "poseLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }
            else -> { /* no-op */ }
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                .setMinTrackingConfidence(minPoseTrackingConfidence)
                .setMinPosePresenceConfidence(minPosePresenceConfidence)
                .setRunningMode(runningMode)

            // LIVE_STREAM 模式需要设置结果和错误监听器
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for details"
            )
            Log.e(TAG, "MediaPipe failed to load the task with error: ${e.message}")
        } catch (e: RuntimeException) {
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for details", GPU_ERROR
            )
            Log.e(TAG, "Image classifier failed to load model with error: ${e.message}")
        }
    }

    // ==================== 1. 实时相机流检测 ====================
    
    /**
     * 对 CameraX ImageProxy 进行实时姿态检测
     * 
     * @param imageProxy CameraX 提供的 ImageProxy
     * @param isFrontCamera 是否使用前置摄像头（用于镜像翻转）
     */
    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        // 将 ImageProxy 转换为 Bitmap
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        // 应用旋转和翻转矩阵
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // 转换为 MPImage 并执行异步检测
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    // ==================== 2. 视频文件检测 ====================
    
    /**
     * 对视频文件进行逐帧姿态检测
     * 
     * @param videoUri 视频文件的 Uri
     * @param inferenceIntervalMs 检测间隔（毫秒），默认每 300ms 检测一帧
     * @return ResultBundle 包含所有帧的检测结果
     */
    fun detectVideoFile(videoUri: Uri, inferenceIntervalMs: Long = 300): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile while not using RunningMode.VIDEO"
            )
        }

        val startTime = SystemClock.uptimeMillis()
        var didErrorOccurred = false

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLong()

        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height

        if (videoLengthMs == null || width == null || height == null) return null

        val resultList = mutableListOf<PoseLandmarkerResult>()
        val numberOfFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        for (i in 0..numberOfFrameToRead) {
            val timestampMs = i * inferenceIntervalMs
            retriever.getFrameAtTime(
                timestampMs * 1000, // 毫秒转微秒
                MediaMetadataRetriever.OPTION_CLOSEST
            )?.let { frame ->
                val argb8888Frame =
                    if (frame.config == Bitmap.Config.ARGB_8888) frame
                    else frame.copy(Bitmap.Config.ARGB_8888, false)

                val mpImage = BitmapImageBuilder(argb8888Frame).build()
                poseLandmarker?.detectForVideo(mpImage, timestampMs)?.let { detectionResult ->
                    resultList.add(detectionResult)
                } ?: run {
                    didErrorOccurred = true
                }
            } ?: run {
                didErrorOccurred = true
            }
        }

        retriever.release()
        val inferenceTimePerFrameMs = (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        return if (didErrorOccurred) {
            null
        } else {
            ResultBundle(resultList, inferenceTimePerFrameMs, height, width)
        }
    }

    // ==================== 3. 单张图片检测 ====================
    
    /**
     * 对单张图片进行姿态检测
     * 
     * @param image Bitmap 图片
     * @return ResultBundle 包含检测结果
     */
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage while not using RunningMode.IMAGE"
            )
        }

        val startTime = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(image).build()

        return poseLandmarker?.detect(mpImage)?.let { landmarkResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            ResultBundle(
                listOf(landmarkResult),
                inferenceTimeMs,
                image.height,
                image.width
            )
        } ?: run {
            poseLandmarkerHelperListener?.onError("Pose Landmarker failed to detect.")
            null
        }
    }

    // ==================== 回调处理 ====================

    private fun returnLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        poseLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(error.message ?: "An unknown error has occurred")
    }

    // ==================== 常量与数据结构 ====================

    companion object {
        const val TAG = "PoseLandmarkerHelper"

        // Delegate 类型
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1

        // 默认置信度阈值
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_POSES = 1

        // 错误码
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        // 模型选择
        const val MODEL_POSE_LANDMARKER_FULL = 0
        const val MODEL_POSE_LANDMARKER_LITE = 1
        const val MODEL_POSE_LANDMARKER_HEAVY = 2
    }

    /** 结果数据包 */
    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int
    )

    /** 结果回调接口 */
    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
```

---

## 6. 三种使用模式详解

### 6.1 单张图片检测（RunningMode.IMAGE）

适用于：相册选图、截图分析等静态图片场景。

```kotlin
class ImageDetectionActivity : AppCompatActivity() {
    
    private lateinit var poseHelper: PoseLandmarkerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image)

        // 1. 初始化 Helper（IMAGE 模式）
        poseHelper = PoseLandmarkerHelper(
            context = this,
            runningMode = RunningMode.IMAGE,
            currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
            currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU
        )

        // 2. 加载图片并检测
        val bitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.sample_image)
        val result = poseHelper.detectImage(bitmap)

        // 3. 处理结果
        result?.results?.forEach { poseResult ->
            poseResult.landmarks().forEach { landmarkList ->
                landmarkList.forEach { point ->
                    val x = point.x()  // 0.0 ~ 1.0 归一化坐标
                    val y = point.y()
                    val z = point.z()
                    Log.d("Pose", "Point: x=$x, y=$y, z=$z")
                }
            }
        }

        // 4. 释放资源
        poseHelper.clearPoseLandmarker()
    }
}
```

### 6.2 视频文件检测（RunningMode.VIDEO）

适用于：分析本地视频文件中的姿态。

```kotlin
class VideoDetectionActivity : AppCompatActivity() {

    private lateinit var poseHelper: PoseLandmarkerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 初始化 Helper（VIDEO 模式）
        poseHelper = PoseLandmarkerHelper(
            context = this,
            runningMode = RunningMode.VIDEO,
            currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
            currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU
        )

        // 2. 指定视频 Uri 并检测（每 300ms 采样一帧）
        val videoUri: Uri = // ... 从文件选择器获取
        val result = poseHelper.detectVideoFile(videoUri, inferenceIntervalMs = 300)

        // 3. 处理结果
        result?.results?.forEachIndexed { frameIndex, poseResult ->
            val landmarkCount = poseResult.landmarks().size
            Log.d("Pose", "Frame $frameIndex: 检测到 $landmarkCount 组姿态")
        }

        // 4. 释放资源
        poseHelper.clearPoseLandmarker()
    }
}
```

### 6.3 实时相机流检测（RunningMode.LIVE_STREAM）

适用于：通过 CameraX 实时捕获摄像头画面并进行姿态检测。

```kotlin
class LiveStreamActivity : AppCompatActivity() {

    private lateinit var poseHelper: PoseLandmarkerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 初始化 Helper（LIVE_STREAM 模式，必须设置监听器）
        poseHelper = PoseLandmarkerHelper(
            context = this,
            runningMode = RunningMode.LIVE_STREAM,
            currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
            currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU,
            poseLandmarkerHelperListener = object : PoseLandmarkerHelper.LandmarkerListener {
                override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
                    // 在主线程更新 UI
                    runOnUiThread {
                        updateOverlay(resultBundle)
                    }
                }

                override fun onError(error: String, errorCode: Int) {
                    Log.e("Pose", "Error: $error")
                }
            }
        )

        // 2. 设置 CameraX（使用 ImageAnalysis）
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
            // 传入 ImageProxy 和是否前置摄像头
            poseHelper.detectLiveStream(imageProxy, isFrontCamera = false)
        }

        // CameraX 绑定...（略）
    }

    private fun updateOverlay(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // 更新 UI 显示检测结果
    }

    override fun onDestroy() {
        super.onDestroy()
        poseHelper.clearPoseLandmarker()
    }
}
```

---

## 7. 检测结果解析

### 7.1 PoseLandmarkerResult 结构

```
PoseLandmarkerResult
├── landmarks(): List<List<NormalizedLandmark>>
│   └── 每组 List<NormalizedLandmark> 代表检测到的一个人的姿态
│       └── 33 个 NormalizedLandmark
├── worldLandmarks(): List<List<Landmark>>
│   └── 世界坐标系下的 3D 坐标（米为单位）
└── segmentationMasks(): List<MPImage>
    └── 分割掩码图像（可选）
```

### 7.2 NormalizedLandmark（归一化坐标）

```kotlin
val landmark = poseResult.landmarks()[0][0]  // 第一个人，第一个点

val x = landmark.x()  // 归一化 X 坐标，范围 [0.0, 1.0]
val y = landmark.y()  // 归一化 Y 坐标，范围 [0.0, 1.0]
val z = landmark.z()  // 相对深度（以髋部中点为原点）
val visibility = landmark.visibility()  // 可见度，范围 [0.0, 1.0]
```

### 7.3 33 个关键点索引对照表

| 索引 | 部位 | 索引 | 部位 |
|------|------|------|------|
| 0 | 鼻子 | 17 | 左小指根 |
| 1 | 左眼内角 | 18 | 右小指根 |
| 2 | 左眼 | 19 | 左食指根 |
| 3 | 左眼外角 | 20 | 右食指根 |
| 4 | 右眼内角 | 21 | 左拇指根 |
| 5 | 右眼 | 22 | 右拇指根 |
| 6 | 右眼外角 | 23 | 左髋 |
| 7 | 左耳 | 24 | 右髋 |
| 8 | 右耳 | 25 | 左膝 |
| 9 | 左嘴角 | 26 | 右膝 |
| 10 | 右嘴角 | 27 | 左踝 |
| 11 | 左肩 | 28 | 右踝 |
| 12 | 右肩 | 29 | 左脚 |
| 13 | 左肘 | 30 | 右脚 |
| 14 | 右肘 | 31 | 左趾 |
| 15 | 左腕 | 32 | 右趾 |
| 16 | 右腕 | | |

### 7.4 骨骼连接定义

```kotlin
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

// 所有预定义的骨骼连接
PoseLandmarker.POSE_LANDMARKS        // 全部连接
PoseLandmarker.POSE_LANDMARKS_LEFT   // 左侧身体连接
PoseLandmarker.POSE_LANDMARKS_RIGHT  // 右侧身体连接
```

每个连接包含 `start()` 和 `end()` 方法，返回对应的关键点索引。

---

## 8. UI 绘制（可选）

使用自定义 `View` 绘制检测到的关键点和骨骼线：

```kotlin
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private val pointPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 12f
        style = Paint.Style.FILL
    }
    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { poseResult ->
            poseResult.landmarks().forEach { landmarkList ->
                // 画点
                landmarkList.forEach { point ->
                    canvas.drawPoint(
                        point.x() * imageWidth * scaleFactor,
                        point.y() * imageHeight * scaleFactor,
                        pointPaint
                    )
                }
                // 画骨骼线
                PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
                    canvas.drawLine(
                        landmarkList[connection.start()].x() * imageWidth * scaleFactor,
                        landmarkList[connection.start()].y() * imageHeight * scaleFactor,
                        landmarkList[connection.end()].x() * imageWidth * scaleFactor,
                        landmarkList[connection.end()].y() * imageHeight * scaleFactor,
                        linePaint
                    )
                }
            }
        }
    }

    fun setResults(
        poseResult: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int
    ) {
        results = poseResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        scaleFactor = min(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }
}
```

---

## 9. 常见问题与注意事项

### 9.1 CPU vs GPU

| 特性 | CPU | GPU |
|------|-----|-----|
| 精度 | 标准 | 相同 |
| 速度 | 较慢 | 较快（支持设备） |
| 线程要求 | 可在任意线程使用 | 必须在初始化线程使用 |
| 兼容性 | 所有设备 | 需设备支持 OpenGL ES 3.1+ |

### 9.2 模型选择建议

| 场景 | 推荐模型 | 原因 |
|------|----------|------|
| 实时相机（30fps）| Lite | 保证帧率 |
| 实时相机（15fps）| Full | 平衡精度与速度 |
| 图片/视频离线处理 | Heavy | 最高精度 |

### 9.3 内存与性能

- **务必在不需要时调用 `clearPoseLandmarker()` 释放资源**
- LIVE_STREAM 模式下，检测结果通过异步回调返回，注意线程切换
- 视频检测时，合理设置 `inferenceIntervalMs` 以平衡精度与性能
- 模型文件体积较大（Full 约 10MB），注意 APK 大小

### 9.4 常见错误

| 错误 | 原因 | 解决 |
|------|------|------|
| `IllegalStateException: poseLandmarkerHelperListener must be set` | LIVE_STREAM 模式未设置监听器 | 创建 Helper 时传入 listener |
| `RuntimeException: Failed to load model` | 模型文件未正确放置 | 检查 assets 目录下的 .task 文件 |
| GPU 初始化失败 | 设备不支持 GPU 加速 | 改用 CPU Delegate |
| 检测不到结果 | 图片中无人或置信度设置过高 | 降低 `minPoseDetectionConfidence` |

---

## 附录：完整文件依赖关系

```
app/src/main/
├── assets/
│   ├── pose_landmarker_full.task   ← 模型文件（自动下载或手动放置）
│   ├── pose_landmarker_lite.task
│   └── pose_landmarker_heavy.task
└── java/com/yourpackage/yourapp/
    ├── PoseLandmarkerHelper.kt      ← 核心封装类
    ├── OverlayView.kt               ← 可选：绘制层
    └── ...
```

---

**文档版本**: 基于 MediaPipe Tasks Vision 0.10.29  
**最后更新**: 2026-06-27
