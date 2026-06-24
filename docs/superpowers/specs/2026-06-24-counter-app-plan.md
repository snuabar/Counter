# Counter App 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建一个安卓端智能计数应用，通过摄像头/麦克风捕获重复动作/声音自动计数，支持内置模板和自定义样本。

**架构：** 采用 MVVM + Repository + Clean Architecture 分层架构，视觉/音频检测引擎完全解耦，传统算法（帧差分/FFT）为主，TFLite 可选扩展。

**技术栈：** Kotlin + Jetpack Compose + Hilt + Room + CameraX + OpenCV + AudioRecord

---

## Phase 1：MVP（预计 4-6 周）

### 目标
可运行的基础计数器：单用户、仅摄像头视觉检测（帧差分）、内置拍手和跳绳模板、基础计数 + 会话保存 + 历史列表。

---

### 任务 1：项目初始化

**文件：**
- 创建：`build.gradle.kts` (project-level)
- 创建：`app/build.gradle.kts`
- 创建：`app/src/main/AndroidManifest.xml`
- 创建：`settings.gradle.kts`

- [ ] **步骤 1：创建 Android Studio 项目**
  - 包名：`com.example.counter`
  - 最低 SDK：API 24 (Android 7.0)
  - 目标 SDK：API 34
  - 语言：Kotlin
  - 构建系统：Gradle with Kotlin DSL

- [ ] **步骤 2：配置项目级 build.gradle.kts**

```kotlin
// Top-level build file
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.0" apply false
}
```

- [ ] **步骤 3：配置模块级 app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.counter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.counter"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime-livedata")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // OpenCV
    implementation("org.opencv:opencv-android:4.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **步骤 4：配置 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />

    <uses-feature android:name="android.hardware.camera" android:required="true" />
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />

    <application
        android:name=".CounterApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Counter">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Counter">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **步骤 5：创建 Application 类**

**文件：** `app/src/main/java/com/example/counter/CounterApplication.kt`

```kotlin
package com.example.counter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CounterApplication : Application()
```

- [ ] **步骤 6：创建 MainActivity**

**文件：** `app/src/main/java/com/example/counter/MainActivity.kt`

```kotlin
package com.example.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.counter.ui.theme.CounterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CounterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // TODO: Navigation setup
                }
            }
        }
    }
}
```

- [ ] **步骤 7：Commit**

```bash
git add .
git commit -m "chore: initialize Android project with Compose, Hilt, Room, CameraX, OpenCV"
```

---

### 任务 2：建立 Clean Architecture 目录结构

**文件：**
- 创建：`app/src/main/java/com/example/counter/data/` 层目录
- 创建：`app/src/main/java/com/example/counter/domain/` 层目录
- 创建：`app/src/main/java/com/example/counter/ui/` 层目录
- 创建：`app/src/main/java/com/example/counter/di/` 层目录

- [ ] **步骤 1：创建目录结构**

```
app/src/main/java/com/example/counter/
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── CounterDatabase.kt
│   │   │   ├── dao/
│   │   │   └── entity/
│   │   └── datasource/
│   └── repository/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── ui/
│   ├── theme/
│   ├── screen/
│   └── component/
├── di/
│   └── AppModule.kt
└── core/
    ├── detection/
    │   ├── VisionDetectionEngine.kt
    │   └── DetectionConfig.kt
    └── util/
```

- [ ] **步骤 2：Commit**

```bash
git add app/src/main/java/com/example/counter/
git commit -m "chore: setup Clean Architecture directory structure"
```

---

### 任务 3：定义 Domain 层数据模型

**文件：**
- 创建：`app/src/main/java/com/example/counter/domain/model/User.kt`
- 创建：`app/src/main/java/com/example/counter/domain/model/CountingSession.kt`
- 创建：`app/src/main/java/com/example/counter/domain/model/CountEvent.kt`
- 创建：`app/src/main/java/com/example/counter/domain/model/Template.kt`

- [ ] **步骤 1：定义 User 模型**

```kotlin
package com.example.counter.domain.model

data class User(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val avatarPath: String? = null
)
```

- [ ] **步骤 2：定义 CountingSession 模型**

```kotlin
package com.example.counter.domain.model

enum class SensorType { VISION, AUDIO }

enum class SessionStatus { RUNNING, PAUSED, COMPLETED, CANCELLED }

data class CountingSession(
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val templateId: Long? = null,
    val sensorType: SensorType,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val targetCount: Int? = null,
    val finalCount: Int = 0,
    val status: SessionStatus = SessionStatus.RUNNING
)
```

- [ ] **步骤 3：定义 CountEvent 模型**

```kotlin
package com.example.counter.domain.model

data class CountEvent(
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val count: Int,
    val confidence: Float
)
```

- [ ] **步骤 4：定义 Template 模型**

```kotlin
package com.example.counter.domain.model

enum class TemplateType { BUILTIN, CUSTOM }

data class Template(
    val id: Long = 0,
    val userId: Long? = null, // null 表示内置模板
    val name: String,
    val type: TemplateType,
    val sensorType: SensorType,
    val mediaPath: String? = null,
    val featureVector: ByteArray? = null,
    val threshold: Float = 0.7f,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/example/counter/domain/model/
git commit -m "feat(domain): define core data models (User, Session, Event, Template)"
```

---

### 任务 4：建立 Room 数据库层

**文件：**
- 创建：`app/src/main/java/com/example/counter/data/local/db/entity/UserEntity.kt`
- 创建：`app/src/main/java/com/example/counter/data/local/db/entity/CountingSessionEntity.kt`
- 创建：`app/src/main/java/com/example/counter/data/local/db/entity/CountEventEntity.kt`
- 创建：`app/src/main/java/com/example/counter/data/local/db/entity/TemplateEntity.kt`
- 创建：`app/src/main/java/com/example/counter/data/local/db/dao/UserDao.kt`
- 创建：`app/src/main/java/com/example/counter/data/local/db/dao/CountingSessionDao.kt`
- 创建：`app/src/main/java/com/example/counter/data/local/db/dao/CountEventDao.kt`
- 创建：`app/src/main/java/com/example/counter/data/local/db/dao/TemplateDao.kt`
- 创建：`app/src/main/java/com/example/counter/data/local/db/CounterDatabase.kt`

- [ ] **步骤 1-4：创建 Entity 类（示例 UserEntity）**

```kotlin
package com.example.counter.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val avatarPath: String? = null
)
```

类似地创建其他 Entity（略）。

- [ ] **步骤 5-8：创建 DAO 接口（示例）**

```kotlin
package com.example.counter.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.counter.data.local.db.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users")
    fun getAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?
}
```

- [ ] **步骤 9：创建 Database 类**

```kotlin
package com.example.counter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.counter.data.local.db.dao.*
import com.example.counter.data.local.db.entity.*

@Database(
    entities = [
        UserEntity::class,
        CountingSessionEntity::class,
        CountEventEntity::class,
        TemplateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CounterDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun countingSessionDao(): CountingSessionDao
    abstract fun countEventDao(): CountEventDao
    abstract fun templateDao(): TemplateDao
}
```

- [ ] **步骤 10：Commit**

```bash
git add app/src/main/java/com/example/counter/data/local/
git commit -m "feat(data): setup Room database with entities and DAOs"
```

---

### 任务 5：创建 Repository 层

**文件：**
- 创建：`app/src/main/java/com/example/counter/domain/repository/CountingSessionRepository.kt`
- 创建：`app/src/main/java/com/example/counter/data/repository/CountingSessionRepositoryImpl.kt`
- 创建：`app/src/main/java/com/example/counter/domain/repository/TemplateRepository.kt`
- 创建：`app/src/main/java/com/example/counter/data/repository/TemplateRepositoryImpl.kt`

- [ ] **步骤 1：定义 Repository 接口**

```kotlin
package com.example.counter.domain.repository

import com.example.counter.domain.model.*
import kotlinx.coroutines.flow.Flow

interface CountingSessionRepository {
    suspend fun createSession(session: CountingSession): Long
    suspend fun updateSession(session: CountingSession)
    suspend fun getSession(id: Long): CountingSession?
    fun getAllSessions(): Flow<List<CountingSession>>
    suspend fun addCountEvent(event: CountEvent)
    fun getCountEvents(sessionId: Long): Flow<List<CountEvent>>
}

interface TemplateRepository {
    suspend fun createTemplate(template: Template): Long
    suspend fun getTemplate(id: Long): Template?
    fun getAllTemplates(): Flow<List<Template>>
    suspend fun getBuiltinTemplates(): List<Template>
}
```

- [ ] **步骤 2：实现 Repository**

```kotlin
package com.example.counter.data.repository

import com.example.counter.data.local.db.dao.*
import com.example.counter.data.local.db.entity.*
import com.example.counter.domain.model.*
import com.example.counter.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CountingSessionRepositoryImpl @Inject constructor(
    private val sessionDao: CountingSessionDao,
    private val eventDao: CountEventDao
) : CountingSessionRepository {

    override suspend fun createSession(session: CountingSession): Long {
        return sessionDao.insert(session.toEntity())
    }

    override suspend fun updateSession(session: CountingSession) {
        sessionDao.update(session.toEntity())
    }

    override suspend fun getSession(id: Long): CountingSession? {
        return sessionDao.getById(id)?.toDomain()
    }

    override fun getAllSessions(): Flow<List<CountingSession>> {
        return sessionDao.getAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun addCountEvent(event: CountEvent) {
        eventDao.insert(event.toEntity())
    }

    override fun getCountEvents(sessionId: Long): Flow<List<CountEvent>> {
        return eventDao.getBySessionId(sessionId).map { list ->
            list.map { it.toDomain() }
        }
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/example/counter/data/repository/
git commit -m "feat(data): implement repository layer with mappers"
```

---

### 任务 6：配置 Hilt 依赖注入

**文件：**
- 创建：`app/src/main/java/com/example/counter/di/AppModule.kt`
- 创建：`app/src/main/java/com/example/counter/di/DatabaseModule.kt`
- 创建：`app/src/main/java/com/example/counter/di/RepositoryModule.kt`

- [ ] **步骤 1：创建 DatabaseModule**

```kotlin
package com.example.counter.di

import android.content.Context
import androidx.room.Room
import com.example.counter.data.local.db.CounterDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CounterDatabase {
        return Room.databaseBuilder(
            context,
            CounterDatabase::class.java,
            "counter_database"
        ).build()
    }

    @Provides
    fun provideUserDao(db: CounterDatabase) = db.userDao()
    @Provides
    fun provideCountingSessionDao(db: CounterDatabase) = db.countingSessionDao()
    @Provides
    fun provideCountEventDao(db: CounterDatabase) = db.countEventDao()
    @Provides
    fun provideTemplateDao(db: CounterDatabase) = db.templateDao()
}
```

- [ ] **步骤 2：创建 RepositoryModule**

```kotlin
package com.example.counter.di

import com.example.counter.data.repository.*
import com.example.counter.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindCountingSessionRepository(
        impl: CountingSessionRepositoryImpl
    ): CountingSessionRepository

    @Binds
    abstract fun bindTemplateRepository(
        impl: TemplateRepositoryImpl
    ): TemplateRepository
}
```

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/example/counter/di/
git commit -m "feat(di): setup Hilt modules for Database and Repository injection"
```

---

### 任务 7：实现视觉检测引擎（核心）

**文件：**
- 创建：`app/src/main/java/com/example/counter/core/detection/DetectionEngine.kt`
- 创建：`app/src/main/java/com/example/counter/core/detection/DetectionConfig.kt`
- 创建：`app/src/main/java/com/example/counter/core/detection/CountEvent.kt`
- 创建：`app/src/main/java/com/example/counter/core/detection/vision/VisionDetectionEngine.kt`
- 创建：`app/src/main/java/com/example/counter/core/detection/vision/FrameDifferencer.kt`
- 创建：`app/src/main/java/com/example/counter/core/detection/vision/MotionDetector.kt`

- [ ] **步骤 1：定义检测引擎接口**

```kotlin
package com.example.counter.core.detection

import kotlinx.coroutines.flow.Flow

interface DetectionEngine {
    fun start(config: DetectionConfig)
    fun pause()
    fun resume()
    fun stop()
    fun setThreshold(threshold: Float)
    val countEvents: Flow<CountEvent>
}

data class DetectionConfig(
    val sensorType: SensorType,
    val templateId: Long? = null,
    val threshold: Float = 0.7f
)

enum class SensorType { VISION, AUDIO }

data class CountEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val count: Int,
    val confidence: Float
)
```

- [ ] **步骤 2：实现帧差分器**

```kotlin
package com.example.counter.core.detection.vision

import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class FrameDifferencer {
    private var previousFrame: Mat? = null

    fun computeDifference(currentFrame: Mat): Double {
        val gray = Mat()
        Imgproc.cvtColor(currentFrame, gray, Imgproc.COLOR_RGB2GRAY)

        val diff = if (previousFrame != null) {
            val result = Mat()
            Core.absdiff(previousFrame, gray, result)
            val mean = Core.mean(result)
            result.release()
            mean.`val`[0]
        } else {
            0.0
        }

        previousFrame?.release()
        previousFrame = gray.clone()
        gray.release()

        return diff
    }

    fun reset() {
        previousFrame?.release()
        previousFrame = null
    }
}
```

- [ ] **步骤 3：实现视觉检测引擎**

```kotlin
package com.example.counter.core.detection.vision

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.counter.core.detection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

class VisionDetectionEngine @Inject constructor(
    private val context: Context
) : DetectionEngine {

    private val _countEvents = MutableSharedFlow<CountEvent>()
    override val countEvents: Flow<CountEvent> = _countEvents.asSharedFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var isRunning = false
    private var isPaused = false
    private var currentCount = 0
    private var threshold = 0.7f
    private var lastCountTime = 0L
    private val debounceMs = 500L // 最小间隔 500ms

    private val frameDifferencer = FrameDifferencer()
    private val motionHistory = mutableListOf<Double>()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun start(config: DetectionConfig) {
        threshold = config.threshold
        currentCount = 0
        lastCountTime = 0
        motionHistory.clear()
        isRunning = true
        isPaused = false
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(android.util.Size(640, 480))
            .build()

        imageAnalysis?.setAnalyzer(executor) { imageProxy ->
            if (!isRunning || isPaused) {
                imageProxy.close()
                return@setAnalyzer
            }

            processFrame(imageProxy)
            imageProxy.close()
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                context as LifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val mat = imageProxy.toMat() ?: return
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_YUV2GRAY_420SP)

        val diff = frameDifferencer.computeDifference(gray)
        motionHistory.add(diff)

        if (motionHistory.size > 30) {
            motionHistory.removeAt(0)
        }

        if (motionHistory.size >= 5) {
            analyzeMotionPattern()
        }

        gray.release()
        mat.release()
    }

    private fun analyzeMotionPattern() {
        if (motionHistory.size < 5) return

        // 简单的峰值检测算法
        val current = motionHistory.last()
        val previous = motionHistory[motionHistory.size - 2]
        val beforePrevious = motionHistory[motionHistory.size - 3]

        // 检测局部峰值：当前 > 前一个 && 当前 > 后一个
        if (current > previous && current > beforePrevious && current > 50.0) {
            val now = System.currentTimeMillis()
            if (now - lastCountTime > debounceMs) {
                currentCount++
                lastCountTime = now
                CoroutineScope(Dispatchers.Main).launch {
                    _countEvents.emit(
                        CountEvent(
                            count = currentCount,
                            confidence = (current / 255.0).toFloat().coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        isPaused = false
    }

    override fun stop() {
        isRunning = false
        isPaused = false
        frameDifferencer.reset()
        motionHistory.clear()
        cameraProvider?.unbindAll()
        executor.shutdown()
    }

    override fun setThreshold(threshold: Float) {
        this.threshold = threshold
    }

    private fun ImageProxy.toMat(): Mat? {
        // 实现 ImageProxy 到 OpenCV Mat 的转换
        // 这里简化处理，实际实现需要根据 ImageProxy 的格式进行转换
        return null // TODO: 实现转换逻辑
    }
}
```

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/example/counter/core/detection/
git commit -m "feat(core): implement vision detection engine with frame differencing and peak detection"
```

---

### 任务 8：实现基础 Compose UI（计数界面）

**文件：**
- 创建：`app/src/main/java/com/example/counter/ui/screen/counting/CountingScreen.kt`
- 创建：`app/src/main/java/com/example/counter/ui/screen/counting/CountingViewModel.kt`
- 创建：`app/src/main/java/com/example/counter/ui/component/CameraPreview.kt`
- 创建：`app/src/main/java/com/example/counter/ui/component/CountDisplay.kt`

- [ ] **步骤 1：创建 CountingViewModel**

```kotlin
package com.example.counter.ui.screen.counting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.counter.core.detection.vision.VisionDetectionEngine
import com.example.counter.core.detection.CountEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CountingViewModel @Inject constructor(
    private val visionDetectionEngine: VisionDetectionEngine
) : ViewModel() {

    private val _currentCount = MutableStateFlow(0)
    val currentCount: StateFlow<Int> = _currentCount.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    init {
        viewModelScope.launch {
            visionDetectionEngine.countEvents.collect { event ->
                _currentCount.value = event.count
                _confidence.value = event.confidence
            }
        }
    }

    fun startCounting() {
        _isRunning.value = true
        visionDetectionEngine.start(
            DetectionConfig(sensorType = SensorType.VISION, threshold = 0.7f)
        )
    }

    fun pauseCounting() {
        _isRunning.value = false
        visionDetectionEngine.pause()
    }

    fun resumeCounting() {
        _isRunning.value = true
        visionDetectionEngine.resume()
    }

    fun stopCounting() {
        _isRunning.value = false
        visionDetectionEngine.stop()
    }

    fun resetCount() {
        _currentCount.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        visionDetectionEngine.stop()
    }
}
```

- [ ] **步骤 2：创建 CountingScreen**

```kotlin
package com.example.counter.ui.screen.counting

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CountingScreen(
    viewModel: CountingViewModel = hiltViewModel()
) {
    val currentCount by viewModel.currentCount.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val confidence by viewModel.confidence.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部：标题
        Text(
            text = "智能计数器",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 32.dp)
        )

        // 中部：计数显示
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentCount.toString(),
                fontSize = 120.sp,
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center
            )

            Text(
                text = "置信度: ${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 底部：操作按钮
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isRunning) {
                Button(
                    onClick = { viewModel.startCounting() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始计数")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.pauseCounting() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("暂停")
                    }
                    Button(
                        onClick = {
                            viewModel.stopCounting()
                            viewModel.resetCount()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("结束")
                    }
                }
            }
        }
    }
}
```

- [ ] **步骤 3：创建 CameraPreview 组件**

```kotlin
package com.example.counter.ui.component

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalContext.current as LifecycleOwner

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}
```

- [ ] **步骤 4：更新 MainActivity 设置 Navigation**

```kotlin
package com.example.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.counter.ui.screen.counting.CountingScreen
import com.example.counter.ui.theme.CounterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CounterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CountingScreen()
                }
            }
        }
    }
}
```

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/example/counter/ui/
git commit -m "feat(ui): implement basic counting screen with Compose, ViewModel and Camera preview"
```

---

### 任务 9：初始化内置模板数据

**文件：**
- 修改：`app/src/main/java/com/example/counter/data/repository/TemplateRepositoryImpl.kt`
- 创建：`app/src/main/java/com/example/counter/data/local/db/initializer/DatabaseInitializer.kt`

- [ ] **步骤 1：创建数据库初始化器，预置拍手和跳绳模板**

```kotlin
package com.example.counter.data.local.db.initializer

import com.example.counter.data.local.db.dao.TemplateDao
import com.example.counter.data.local.db.entity.TemplateEntity
import com.example.counter.domain.model.SensorType
import com.example.counter.domain.model.TemplateType
import javax.inject.Inject

class DatabaseInitializer @Inject constructor(
    private val templateDao: TemplateDao
) {
    suspend fun initializeBuiltinTemplates() {
        val existingCount = templateDao.getBuiltinTemplatesCount()
        if (existingCount > 0) return

        val builtinTemplates = listOf(
            TemplateEntity(
                id = 1,
                userId = null, // null 表示内置模板
                name = "拍手",
                type = TemplateType.BUILTIN.name,
                sensorType = SensorType.VISION.name,
                mediaPath = null,
                featureVector = null,
                threshold = 0.7f
            ),
            TemplateEntity(
                id = 2,
                userId = null,
                name = "跳绳",
                type = TemplateType.BUILTIN.name,
                sensorType = SensorType.VISION.name,
                mediaPath = null,
                featureVector = null,
                threshold = 0.7f
            )
        )

        builtinTemplates.forEach { templateDao.insert(it) }
    }
}
```

- [ ] **步骤 2：在 Application 初始化时调用**

```kotlin
package com.example.counter

import android.app.Application
import com.example.counter.data.local.db.initializer.DatabaseInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class CounterApplication : Application() {

    @Inject
    lateinit var databaseInitializer: DatabaseInitializer

    override fun onCreate() {
        super.onCreate()
        runBlocking {
            databaseInitializer.initializeBuiltinTemplates()
        }
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/example/counter/data/local/db/initializer/
git commit -m "feat(data): initialize builtin templates (clap, jump-rope) on app start"
```

---

### 任务 10：实现会话管理和历史记录

**文件：**
- 创建：`app/src/main/java/com/example/counter/domain/usecase/SaveSessionUseCase.kt`
- 创建：`app/src/main/java/com/example/counter/domain/usecase/GetSessionHistoryUseCase.kt`
- 创建：`app/src/main/java/com/example/counter/ui/screen/history/HistoryScreen.kt`
- 创建：`app/src/main/java/com/example/counter/ui/screen/history/HistoryViewModel.kt`

- [ ] **步骤 1：创建 Use Case**

```kotlin
package com.example.counter.domain.usecase

import com.example.counter.domain.model.*
import com.example.counter.domain.repository.*
import javax.inject.Inject

class SaveSessionUseCase @Inject constructor(
    private val sessionRepository: CountingSessionRepository
) {
    suspend operator fun invoke(session: CountingSession): Long {
        return sessionRepository.createSession(session)
    }
}

class GetSessionHistoryUseCase @Inject constructor(
    private val sessionRepository: CountingSessionRepository
) {
    operator fun invoke() = sessionRepository.getAllSessions()
}
```

- [ ] **步骤 2：创建 HistoryScreen**

```kotlin
package com.example.counter.ui.screen.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.counter.domain.model.CountingSession
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "历史记录",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn {
            items(sessions) { session ->
                SessionItem(session = session)
            }
        }
    }
}

@Composable
private fun SessionItem(session: CountingSession) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = session.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "次数: ${session.finalCount}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(session.startTime)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/example/counter/domain/usecase/
git add app/src/main/java/com/example/counter/ui/screen/history/
git commit -m "feat(history): implement session management and history screen"
```

---

### 任务 11：添加导航和 Tab 栏

**文件：**
- 创建：`app/src/main/java/com/example/counter/ui/navigation/NavGraph.kt`
- 创建：`app/src/main/java/com/example/counter/ui/navigation/Screen.kt`
- 修改：`app/src/main/java/com/example/counter/MainActivity.kt`

- [ ] **步骤 1：定义导航路由**

```kotlin
package com.example.counter.ui.navigation

sealed class Screen(val route: String) {
    object Counting : Screen("counting")
    object History : Screen("history")
    object Analysis : Screen("analysis")
    object Settings : Screen("settings")
}
```

- [ ] **步骤 2：创建导航图**

```kotlin
package com.example.counter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.counter.ui.screen.counting.CountingScreen
import com.example.counter.ui.screen.history.HistoryScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Counting.route
    ) {
        composable(Screen.Counting.route) {
            CountingScreen()
        }
        composable(Screen.History.route) {
            HistoryScreen()
        }
        // TODO: Analysis and Settings screens
    }
}
```

- [ ] **步骤 3：创建主布局（含底部 Tab 栏）**

```kotlin
package com.example.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.counter.ui.navigation.NavGraph
import com.example.counter.ui.navigation.Screen
import com.example.counter.ui.theme.CounterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CounterTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val items = listOf(
                                Screen.Counting,
                                Screen.History,
                                Screen.Analysis,
                                Screen.Settings
                            )
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { /* TODO: Icon */ },
                                    label = { Text(screen.route) },
                                    selected = currentRoute == screen.route,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        NavGraph(navController = navController)
                    }
                }
            }
        }
    }
}
```

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/example/counter/ui/navigation/
git add app/src/main/java/com/example/counter/MainActivity.kt
git commit -m "feat(navigation): add bottom tab navigation with Compose Navigation"
```

---

## Phase 2：完整功能（预计 3-4 周）

### 目标
多用户、音频检测引擎、自定义样本录制与模板匹配、阈值调节、数据分析与图表、数据导入导出备份。

### 任务列表

#### 任务 12：多用户支持
- [ ] 创建用户管理 UI（创建、切换、删除用户）
- [ ] 在 Repository 层实现 userId 过滤
- [ ] 在用户切换时刷新所有数据

#### 任务 13：音频检测引擎
- [ ] 使用 AudioRecord 实现音频采集
- [ ] 实现 FFT 频谱分析
- [ ] 实现音频能量峰值检测
- [ ] 集成到 DetectionEngine 接口

#### 任务 14：自定义样本录制
- [ ] 实现样本录制界面（CameraX / AudioRecord）
- [ ] 实现特征提取（视觉：光流直方图；音频：MFCC）
- [ ] 保存样本到本地文件 + 特征向量到数据库

#### 任务 15：模板匹配
- [ ] 实现视觉模板匹配（余弦相似度 / 欧氏距离）
- [ ] 实现音频模板匹配（DTW 距离）
- [ ] 在检测引擎中集成模板匹配逻辑

#### 任务 16：阈值调节
- [ ] 在设置界面添加滑块控件
- [ ] 实时调节阈值并保存到数据库
- [ ] 在检测引擎中应用动态阈值

#### 任务 17：数据分析与图表
- [ ] 集成 MPAndroidChart 或 Compose Charts
- [ ] 实现趋势图（周/月/年）
- [ ] 实现统计数据展示（总次数、平均频率等）

#### 任务 18：数据导出/导入备份
- [ ] 实现 JSON 导出功能
- [ ] 实现 JSON 导入恢复功能
- [ ] 添加文件选择器 UI

---

## Phase 3：增强优化（预计 2-3 周）

### 目标
TFLite 可选模型、远程备份接口预留、UI 美化、性能优化、单元测试。

### 任务列表

#### 任务 19：TFLite 集成
- [ ] 添加 TensorFlow Lite 依赖
- [ ] 实现 TFLite 检测引擎（实现 DetectionEngine 接口）
- [ ] 预训练模型管理（下载/缓存）
- [ ] 让用户在传统算法和 TFLite 之间切换

#### 任务 20：远程备份接口
- [ ] 设计备份接口抽象
- [ ] 预留 WebDAV / 自定义 API 扩展点
- [ ] 实现基础的网络备份 UI

#### 任务 21：UI 美化
- [ ] 设计并应用 Material You 主题色
- [ ] 添加过渡动画
- [ ] 优化不同屏幕尺寸的适配

#### 任务 22：性能优化
- [ ] 相机分辨率/帧率降级选项
- [ ] 低端设备适配
- [ ] 电池优化（后台限制）

#### 任务 23：单元测试
- [ ] 为 Repository 层编写单元测试
- [ ] 为检测引擎编写单元测试
- [ ] 为 ViewModel 编写单元测试
- [ ] 集成测试（UI 流程）

---

## 关键难点实现方案

### 1. ImageProxy 转 OpenCV Mat
```kotlin
fun ImageProxy.toMat(): Mat {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val mat = Mat(height, width, CvType.CV_8UC1)
    mat.put(0, 0, bytes)
    return mat
}
```

### 2. 帧差分峰值检测
- 使用滑动窗口计算运动强度
- 应用低通滤波平滑噪声
- 检测局部峰值，使用最小间隔过滤（debounce）

### 3. 多用户数据隔离
- 所有 DAO 查询添加 userId WHERE 条件
- Repository 层统一过滤
- 切换用户时清空内存缓存

---

## 测试策略

### 单元测试
- **Repository 层**：使用 Room in-memory 数据库测试所有 CRUD 操作
- **检测引擎**：使用模拟的帧/音频数据测试算法逻辑
- **ViewModel**：使用 kotlinx-coroutines-test 测试状态管理

### 集成测试
- **UI 流程**：使用 Compose UI Test 测试计数流程
- **相机集成**：使用 CameraX 测试工具验证预览和帧分析

### 手动测试
- **不同光照条件**：室内、室外、逆光
- **不同设备**：高端/中端/低端设备
- **长时间运行**：1小时以上的稳定性测试

---

## 验收标准

### Phase 1 验收标准
- [ ] 应用可以正常启动并显示计数界面
- [ ] 摄像头预览正常显示
- [ ] 拍手动作可以被正确计数（准确率 > 80%）
- [ ] 计数结果可以保存到本地数据库
- [ ] 历史记录页面可以查看已保存的会话
- [ ] 应用可以稳定运行 30 分钟以上不崩溃

### Phase 2 验收标准
- [ ] 支持多用户切换
- [ ] 音频检测可以正确计数（拍手/跳绳声音）
- [ ] 用户可以录制自定义样本并用于计数
- [ ] 阈值调节实时生效
- [ ] 数据分析页面展示正确的统计图表
- [ ] 数据可以导出为 JSON 并导入恢复

### Phase 3 验收标准
- [ ] TFLite 模型可选并提高复杂动作识别率
- [ ] 远程备份接口预留完成
- [ ] UI 在所有目标设备上显示正常
- [ ] 低端设备运行流畅（帧率 > 15fps）
- [ ] 单元测试覆盖率 > 60%
