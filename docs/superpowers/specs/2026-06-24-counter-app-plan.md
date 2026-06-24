# Counter App 螳樒鴫隶｡蛻・

> **髱｢蜷・AI 莉｣逅・噪蟾･菴懆・ｼ・* 蠢・怙蟄先橿閭ｽ・壻ｽｿ逕ｨ superpowers:subagent-driven-development・域耳闕撰ｼ画・ superpowers:executing-plans 騾蝉ｻｻ蜉｡螳樒鴫豁､隶｡蛻偵よｭ･鬪､菴ｿ逕ｨ螟埼画｡・ｼ・- [ ]`・芽ｯｭ豕墓擂霍溯ｸｪ霑帛ｺｦ縲・

**逶ｮ譬・ｼ・* 譫・ｻｺ荳荳ｪ螳牙酷遶ｯ譎ｺ閭ｽ隶｡謨ｰ蠎皮畑・碁夊ｿ・槍蜒丞､ｴ/鮗ｦ蜈矩｣取黒闔ｷ驥榊､榊勘菴・螢ｰ髻ｳ閾ｪ蜉ｨ隶｡謨ｰ・梧髪謖∝・鄂ｮ讓｡譚ｿ蜥瑚・螳壻ｹ画ｷ譛ｬ縲・

**譫ｶ譫・ｼ・* 驥・畑 MVVM + Repository + Clean Architecture 蛻・ｱよ楔譫・ｼ瑚ｧ・ｧ・髻ｳ鬚第｣豬句ｼ墓梼螳悟・隗｣閠ｦ・御ｼ扈溽ｮ玲ｳ包ｼ亥ｸｧ蟾ｮ蛻・FFT・我ｸｺ荳ｻ・卦FLite 蜿ｯ騾画黄螻輔・

**謚譛ｯ譬茨ｼ・* Kotlin + Jetpack Compose + Hilt + Room + CameraX + OpenCV + AudioRecord

---

## Phase 1・哺VP・磯｢・ｮ｡ 4-6 蜻ｨ・・

### 逶ｮ譬・
蜿ｯ霑占｡檎噪蝓ｺ遑隶｡謨ｰ蝎ｨ・壼黒逕ｨ謌ｷ縲∽ｻ・槍蜒丞､ｴ隗・ｧ画｣豬具ｼ亥ｸｧ蟾ｮ蛻・ｼ峨∝・鄂ｮ諡肴焔蜥瑚ｷｳ扈ｳ讓｡譚ｿ縲∝渕遑隶｡謨ｰ + 莨夊ｯ昜ｿ晏ｭ・+ 蜴・彰蛻苓｡ｨ縲・

---

### 莉ｻ蜉｡ 1・夐｡ｹ逶ｮ蛻晏ｧ句喧

**譁・ｻｶ・・*
- 蛻帛ｻｺ・啻build.gradle.kts` (project-level)
- 蛻帛ｻｺ・啻app/build.gradle.kts`
- 蛻帛ｻｺ・啻app/src/main/AndroidManifest.xml`
- 蛻帛ｻｺ・啻settings.gradle.kts`

- [ ] **豁･鬪､ 1・壼・蟒ｺ Android Studio 鬘ｹ逶ｮ**
  - 蛹・錐・啻com.snuabar.counter`
  - 譛菴・SDK・哂PI 24 (Android 7.0)
  - 逶ｮ譬・SDK・哂PI 34
  - 隸ｭ險・哮otlin
  - 譫・ｻｺ邉ｻ扈滂ｼ哦radle with Kotlin DSL

- [ ] **豁･鬪､ 2・夐・鄂ｮ鬘ｹ逶ｮ郤ｧ build.gradle.kts**

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

- [ ] **豁･鬪､ 3・夐・鄂ｮ讓｡蝮礼ｺｧ app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.snuabar.counter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.snuabar.counter"
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

- [ ] **豁･鬪､ 4・夐・鄂ｮ AndroidManifest.xml**

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

- [ ] **豁･鬪､ 5・壼・蟒ｺ Application 邀ｻ**

**譁・ｻｶ・・* `app/src/main/java/com/example/counter/CounterApplication.kt`

```kotlin
package com.snuabar.counter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CounterApplication : Application()
```

- [ ] **豁･鬪､ 6・壼・蟒ｺ MainActivity**

**譁・ｻｶ・・* `app/src/main/java/com/example/counter/MainActivity.kt`

```kotlin
package com.snuabar.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.snuabar.counter.ui.theme.CounterTheme

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

- [ ] **豁･鬪､ 7・咾ommit**

```bash
git add .
git commit -m "chore: initialize Android project with Compose, Hilt, Room, CameraX, OpenCV"
```

---

### 莉ｻ蜉｡ 2・壼ｻｺ遶・Clean Architecture 逶ｮ蠖慕ｻ捺桷

**譁・ｻｶ・・*
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/` 螻ら岼蠖・
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/domain/` 螻ら岼蠖・
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/ui/` 螻ら岼蠖・
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/di/` 螻ら岼蠖・

- [ ] **豁･鬪､ 1・壼・蟒ｺ逶ｮ蠖慕ｻ捺桷**

```
app/src/main/java/com/example/counter/
笏懌楳笏 data/
笏・  笏懌楳笏 local/
笏・  笏・  笏懌楳笏 db/
笏・  笏・  笏・  笏懌楳笏 CounterDatabase.kt
笏・  笏・  笏・  笏懌楳笏 dao/
笏・  笏・  笏・  笏披楳笏 entity/
笏・  笏・  笏披楳笏 datasource/
笏・  笏披楳笏 repository/
笏懌楳笏 domain/
笏・  笏懌楳笏 model/
笏・  笏懌楳笏 repository/
笏・  笏披楳笏 usecase/
笏懌楳笏 ui/
笏・  笏懌楳笏 theme/
笏・  笏懌楳笏 screen/
笏・  笏披楳笏 component/
笏懌楳笏 di/
笏・  笏披楳笏 AppModule.kt
笏披楳笏 core/
    笏懌楳笏 detection/
    笏・  笏懌楳笏 VisionDetectionEngine.kt
    笏・  笏披楳笏 DetectionConfig.kt
    笏披楳笏 util/
```

- [ ] **豁･鬪､ 2・咾ommit**

```bash
git add app/src/main/java/com/example/counter/
git commit -m "chore: setup Clean Architecture directory structure"
```

---

### 莉ｻ蜉｡ 3・壼ｮ壻ｹ・Domain 螻よ焚謐ｮ讓｡蝙・

**譁・ｻｶ・・*
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/domain/model/User.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/domain/model/CountingSession.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/domain/model/CountEvent.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/domain/model/Template.kt`

- [ ] **豁･鬪､ 1・壼ｮ壻ｹ・User 讓｡蝙・*

```kotlin
package com.snuabar.counter.domain.model

data class User(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val avatarPath: String? = null
)
```

- [ ] **豁･鬪､ 2・壼ｮ壻ｹ・CountingSession 讓｡蝙・*

```kotlin
package com.snuabar.counter.domain.model

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

- [ ] **豁･鬪､ 3・壼ｮ壻ｹ・CountEvent 讓｡蝙・*

```kotlin
package com.snuabar.counter.domain.model

data class CountEvent(
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val count: Int,
    val confidence: Float
)
```

- [ ] **豁･鬪､ 4・壼ｮ壻ｹ・Template 讓｡蝙・*

```kotlin
package com.snuabar.counter.domain.model

enum class TemplateType { BUILTIN, CUSTOM }

data class Template(
    val id: Long = 0,
    val userId: Long? = null, // null 陦ｨ遉ｺ蜀・ｽｮ讓｡譚ｿ
    val name: String,
    val type: TemplateType,
    val sensorType: SensorType,
    val mediaPath: String? = null,
    val featureVector: ByteArray? = null,
    val threshold: Float = 0.7f,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **豁･鬪､ 5・咾ommit**

```bash
git add app/src/main/java/com/example/counter/domain/model/
git commit -m "feat(domain): define core data models (User, Session, Event, Template)"
```

---

### 莉ｻ蜉｡ 4・壼ｻｺ遶・Room 謨ｰ謐ｮ蠎灘ｱ・

**譁・ｻｶ・・*
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/local/db/entity/UserEntity.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/local/db/entity/CountingSessionEntity.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/local/db/entity/CountEventEntity.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/local/db/entity/TemplateEntity.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/local/db/dao/UserDao.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/local/db/dao/CountingSessionDao.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/local/db/dao/CountEventDao.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/local/db/dao/TemplateDao.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/local/db/CounterDatabase.kt`

- [ ] **豁･鬪､ 1-4・壼・蟒ｺ Entity 邀ｻ・育､ｺ萓・UserEntity・・*

```kotlin
package com.snuabar.counter.data.local.db.entity

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

邀ｻ莨ｼ蝨ｰ蛻帛ｻｺ蜈ｶ莉・Entity・育払・峨・

- [ ] **豁･鬪､ 5-8・壼・蟒ｺ DAO 謗･蜿｣・育､ｺ萓具ｼ・*

```kotlin
package com.snuabar.counter.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.snuabar.counter.data.local.db.entity.UserEntity
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

- [ ] **豁･鬪､ 9・壼・蟒ｺ Database 邀ｻ**

```kotlin
package com.snuabar.counter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.snuabar.counter.data.local.db.dao.*
import com.snuabar.counter.data.local.db.entity.*

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

- [ ] **豁･鬪､ 10・咾ommit**

```bash
git add app/src/main/java/com/example/counter/data/local/
git commit -m "feat(data): setup Room database with entities and DAOs"
```

---

### 莉ｻ蜉｡ 5・壼・蟒ｺ Repository 螻・

**譁・ｻｶ・・*
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/domain/repository/CountingSessionRepository.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/repository/CountingSessionRepositoryImpl.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/domain/repository/TemplateRepository.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/repository/TemplateRepositoryImpl.kt`

- [ ] **豁･鬪､ 1・壼ｮ壻ｹ・Repository 謗･蜿｣**

```kotlin
package com.snuabar.counter.domain.repository

import com.snuabar.counter.domain.model.*
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

- [ ] **豁･鬪､ 2・壼ｮ樒鴫 Repository**

```kotlin
package com.snuabar.counter.data.repository

import com.snuabar.counter.data.local.db.dao.*
import com.snuabar.counter.data.local.db.entity.*
import com.snuabar.counter.domain.model.*
import com.snuabar.counter.domain.repository.*
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

- [ ] **豁･鬪､ 3・咾ommit**

```bash
git add app/src/main/java/com/example/counter/data/repository/
git commit -m "feat(data): implement repository layer with mappers"
```

---

### 莉ｻ蜉｡ 6・夐・鄂ｮ Hilt 萓晁ｵ匁ｳｨ蜈･

**譁・ｻｶ・・*
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/di/AppModule.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/di/DatabaseModule.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/di/RepositoryModule.kt`

- [ ] **豁･鬪､ 1・壼・蟒ｺ DatabaseModule**

```kotlin
package com.snuabar.counter.di

import android.content.Context
import androidx.room.Room
import com.snuabar.counter.data.local.db.CounterDatabase
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

- [ ] **豁･鬪､ 2・壼・蟒ｺ RepositoryModule**

```kotlin
package com.snuabar.counter.di

import com.snuabar.counter.data.repository.*
import com.snuabar.counter.domain.repository.*
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

- [ ] **豁･鬪､ 3・咾ommit**

```bash
git add app/src/main/java/com/example/counter/di/
git commit -m "feat(di): setup Hilt modules for Database and Repository injection"
```

---

### 莉ｻ蜉｡ 7・壼ｮ樒鴫隗・ｧ画｣豬句ｼ墓梼・域ｸ蠢・ｼ・

**譁・ｻｶ・・*
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/core/detection/DetectionEngine.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/core/detection/DetectionConfig.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/core/detection/CountEvent.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/core/detection/vision/VisionDetectionEngine.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/core/detection/vision/FrameDifferencer.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/core/detection/vision/MotionDetector.kt`

- [ ] **豁･鬪､ 1・壼ｮ壻ｹ画｣豬句ｼ墓梼謗･蜿｣**

```kotlin
package com.snuabar.counter.core.detection

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

- [ ] **豁･鬪､ 2・壼ｮ樒鴫蟶ｧ蟾ｮ蛻・勣**

```kotlin
package com.snuabar.counter.core.detection.vision

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

- [ ] **豁･鬪､ 3・壼ｮ樒鴫隗・ｧ画｣豬句ｼ墓梼**

```kotlin
package com.snuabar.counter.core.detection.vision

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.snuabar.counter.core.detection.*
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
    private val debounceMs = 500L // 譛蟆城龍髫・500ms

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

        // 邂蜊慕噪蟲ｰ蛟ｼ譽豬狗ｮ玲ｳ・
        val current = motionHistory.last()
        val previous = motionHistory[motionHistory.size - 2]
        val beforePrevious = motionHistory[motionHistory.size - 3]

        // 譽豬句ｱ驛ｨ蟲ｰ蛟ｼ・壼ｽ灘燕 > 蜑堺ｸ荳ｪ && 蠖灘燕 > 蜷惹ｸ荳ｪ
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
        // 螳樒鴫 ImageProxy 蛻ｰ OpenCV Mat 逧・ｽｬ謐｢
        // 霑咎㈹邂蛹門､・炊・悟ｮ樣刔螳樒鴫髴隕∵ｹ謐ｮ ImageProxy 逧・ｼ蠑剰ｿ幄｡瑚ｽｬ謐｢
        return null // TODO: 螳樒鴫霓ｬ謐｢騾ｻ霎・
    }
}
```

- [ ] **豁･鬪､ 4・咾ommit**

```bash
git add app/src/main/java/com/example/counter/core/detection/
git commit -m "feat(core): implement vision detection engine with frame differencing and peak detection"
```

---

### 莉ｻ蜉｡ 8・壼ｮ樒鴫蝓ｺ遑 Compose UI・郁ｮ｡謨ｰ逡碁擇・・

**譁・ｻｶ・・*
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/ui/screen/counting/CountingScreen.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/ui/screen/counting/CountingViewModel.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/ui/component/CameraPreview.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/ui/component/CountDisplay.kt`

- [ ] **豁･鬪､ 1・壼・蟒ｺ CountingViewModel**

```kotlin
package com.snuabar.counter.ui.screen.counting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.core.detvision.VisionDetectionEngine
import com.snuabar.counter.core.detection.CountEvent
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

- [ ] **豁･鬪､ 2・壼・蟒ｺ CountingScreen**

```kotlin
package com.snuabar.counter.ui.screen.counting

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
        // 鬘ｶ驛ｨ・壽・｢・
        Text(
            text = "譎ｺ閭ｽ隶｡謨ｰ蝎ｨ",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 32.dp)
        )

        // 荳ｭ驛ｨ・夊ｮ｡謨ｰ譏ｾ遉ｺ
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
                text = "鄂ｮ菫｡蠎ｦ: ${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 蠎暮Κ・壽桃菴懈潔髓ｮ
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isRunning) {
                Button(
                    onClick = { viewModel.startCounting() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("蠑蟋玖ｮ｡謨ｰ")
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
                        Text("證ょ●")
                    }
                    Button(
                        onClick = {
                            viewModel.stopCounting()
                            viewModel.resetCount()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("扈捺據")
                    }
                }
            }
        }
    }
}
```

- [ ] **豁･鬪､ 3・壼・蟒ｺ CameraPreview 扈・ｻｶ**

```kotlin
package com.snuabar.counter.ui.component

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

- [ ] **豁･鬪､ 4・壽峩譁ｰ MainActivity 隶ｾ鄂ｮ Navigation**

```kotlin
package com.snuabar.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.snuabar.counter.ui.screen.counting.CountingScreen
import com.snuabar.counter.ui.theme.CounterTheme
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

- [ ] **豁･鬪､ 5・咾ommit**

```bash
git add app/src/main/java/com/example/counter/ui/
git commit -m "feat(ui): implement basic counting screen with Compose, ViewModel and Camera preview"
```

---

### 莉ｻ蜉｡ 9・壼・蟋句喧蜀・ｽｮ讓｡譚ｿ謨ｰ謐ｮ

**譁・ｻｶ・・*
- 菫ｮ謾ｹ・啻app/src/main/java/com/example/counter/data/repository/TemplateRepositoryImpl.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/data/local/db/initializer/DatabaseInitializer.kt`

- [ ] **豁･鬪､ 1・壼・蟒ｺ謨ｰ謐ｮ蠎灘・蟋句喧蝎ｨ・碁｢・ｽｮ諡肴焔蜥瑚ｷｳ扈ｳ讓｡譚ｿ**

```kotlin
package com.snuabar.counter.data.local.db.initializer

import com.snuabar.counter.data.local.db.dao.TemplateDao
import com.snuabar.counter.data.local.db.entity.TemplateEntity
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.TemplateType
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
                userId = null, // null 陦ｨ遉ｺ蜀・ｽｮ讓｡譚ｿ
                name = "諡肴焔",
                type = TemplateType.BUILTIN.name,
                sensorType = SensorType.VISION.name,
                mediaPath = null,
                featureVector = null,
                threshold = 0.7f
            ),
            TemplateEntity(
                id = 2,
                userId = null,
                name = "霍ｳ扈ｳ",
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

- [ ] **豁･鬪､ 2・壼惠 Application 蛻晏ｧ句喧譌ｶ隹・畑**

```kotlin
package com.snuabar.counter

import android.app.Application
import com.snuabar.counter.data.local.db.initializer.DatabaseInitializer
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

- [ ] **豁･鬪､ 3・咾ommit**

```bash
git add app/src/main/java/com/example/counter/data/local/db/initializer/
git commit -m "feat(data): initialize builtin templates (clap, jump-rope) on app start"
```

---

### 莉ｻ蜉｡ 10・壼ｮ樒鴫莨夊ｯ晉ｮ｡逅・柱蜴・彰隶ｰ蠖・

**譁・ｻｶ・・*
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/domain/usecase/SaveSessionUseCase.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/domain/usecase/GetSessionHistoryUseCase.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/ui/screen/history/HistoryScreen.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/ui/screen/history/HistoryViewModel.kt`

- [ ] **豁･鬪､ 1・壼・蟒ｺ Use Case**

```kotlin
package com.snuabar.counter.domain.usecase

import com.snuabar.counter.domain.model.*
import com.snuabar.counter.domain.repository.*
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

- [ ] **豁･鬪､ 2・壼・蟒ｺ HistoryScreen**

```kotlin
package com.snuabar.counter.ui.screen.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.domain.model.CountingSession
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
            text = "蜴・彰隶ｰ蠖・,
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
                text = "谺｡謨ｰ: ${session.finalCount}",
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

- [ ] **豁･鬪､ 3・咾ommit**

```bash
git add app/src/main/java/com/example/counter/domain/usecase/
git add app/src/main/java/com/example/counter/ui/screen/history/
git commit -m "feat(history): implement session management and history screen"
```

---

### 莉ｻ蜉｡ 11・壽ｷｻ蜉蟇ｼ闊ｪ蜥・Tab 譬・

**譁・ｻｶ・・*
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/ui/navigation/NavGraph.kt`
- 蛻帛ｻｺ・啻app/src/main/java/com/example/counter/ui/navigation/Screen.kt`
- 菫ｮ謾ｹ・啻app/src/main/java/com/example/counter/MainActivity.kt`

- [ ] **豁･鬪､ 1・壼ｮ壻ｹ牙ｯｼ闊ｪ霍ｯ逕ｱ**

```kotlin
package com.snuabar.counter.ui.navigation

sealed class Screen(val route: String) {
    object Counting : Screen("counting")
    object History : Screen("history")
    object Analysis : Screen("analysis")
    object Settings : Screen("settings")
}
```

- [ ] **豁･鬪､ 2・壼・蟒ｺ蟇ｼ闊ｪ蝗ｾ**

```kotlin
package com.snuabar.counter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.snuabar.counter.ui.screen.counting.CountingScreen
import com.snuabar.counter.ui.screen.history.HistoryScreen

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

- [ ] **豁･鬪､ 3・壼・蟒ｺ荳ｻ蟶・ｱ・亥性蠎暮Κ Tab 譬擾ｼ・*

```kotlin
package com.snuabar.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.snuabar.counter.ui.navigation.NavGraph
import com.snuabar.counter.ui.navigation.Screen
import com.snuabar.counter.ui.theme.CounterTheme
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

- [ ] **豁･鬪､ 4・咾ommit**

```bash
git add app/src/main/java/com/example/counter/ui/navigation/
git add app/src/main/java/com/example/counter/MainActivity.kt
git commit -m "feat(navigation): add bottom tab navigation with Compose Navigation"
```

---

## Phase 2・壼ｮ梧紛蜉溯・・磯｢・ｮ｡ 3-4 蜻ｨ・・

### 逶ｮ譬・
螟夂畑謌ｷ縲・浹鬚第｣豬句ｼ墓梼縲∬・螳壻ｹ画ｷ譛ｬ蠖募宛荳取ｨ｡譚ｿ蛹ｹ驟阪・・蛟ｼ隹・鰍縲∵焚謐ｮ蛻・梵荳主崟陦ｨ縲∵焚謐ｮ蟇ｼ蜈･蟇ｼ蜃ｺ螟・ｻｽ縲・

### 莉ｻ蜉｡蛻苓｡ｨ

#### 莉ｻ蜉｡ 12・壼､夂畑謌ｷ謾ｯ謖・
- [ ] 蛻帛ｻｺ逕ｨ謌ｷ邂｡逅・UI・亥・蟒ｺ縲∝・謐｢縲∝唖髯､逕ｨ謌ｷ・・
- [ ] 蝨ｨ Repository 螻ょｮ樒鴫 userId 霑・ｻ､
- [ ] 蝨ｨ逕ｨ謌ｷ蛻・困譌ｶ蛻ｷ譁ｰ謇譛画焚謐ｮ

#### 莉ｻ蜉｡ 13・夐浹鬚第｣豬句ｼ墓梼
- [ ] 菴ｿ逕ｨ AudioRecord 螳樒鴫髻ｳ鬚鷹㊦髮・
- [ ] 螳樒鴫 FFT 鬚題ｰｱ蛻・梵
- [ ] 螳樒鴫髻ｳ鬚題・驥丞ｳｰ蛟ｼ譽豬・
- [ ] 髮・・蛻ｰ DetectionEngine 謗･蜿｣

#### 莉ｻ蜉｡ 14・夊・螳壻ｹ画ｷ譛ｬ蠖募宛
- [ ] 螳樒鴫譬ｷ譛ｬ蠖募宛逡碁擇・・ameraX / AudioRecord・・
- [ ] 螳樒鴫迚ｹ蠕∵署蜿厄ｼ郁ｧ・ｧ会ｼ壼・豬∫峩譁ｹ蝗ｾ・幃浹鬚托ｼ哺FCC・・
- [ ] 菫晏ｭ俶ｷ譛ｬ蛻ｰ譛ｬ蝨ｰ譁・ｻｶ + 迚ｹ蠕∝髄驥丞芦謨ｰ謐ｮ蠎・

#### 莉ｻ蜉｡ 15・壽ｨ｡譚ｿ蛹ｹ驟・
- [ ] 螳樒鴫隗・ｧ画ｨ｡譚ｿ蛹ｹ驟搾ｼ井ｽ吝ｼｦ逶ｸ莨ｼ蠎ｦ / 谺ｧ豌剰ｷ晉ｦｻ・・
- [ ] 螳樒鴫髻ｳ鬚第ｨ｡譚ｿ蛹ｹ驟搾ｼ・TW 霍晉ｦｻ・・
- [ ] 蝨ｨ譽豬句ｼ墓梼荳ｭ髮・・讓｡譚ｿ蛹ｹ驟埼ｻ霎・

#### 莉ｻ蜉｡ 16・夐・蛟ｼ隹・鰍
- [ ] 蝨ｨ隶ｾ鄂ｮ逡碁擇豺ｻ蜉貊大摎謗ｧ莉ｶ
- [ ] 螳樊慮隹・鰍髦亥ｼ蟷ｶ菫晏ｭ伜芦謨ｰ謐ｮ蠎・
- [ ] 蝨ｨ譽豬句ｼ墓梼荳ｭ蠎皮畑蜉ｨ諤・・蛟ｼ

#### 莉ｻ蜉｡ 17・壽焚謐ｮ蛻・梵荳主崟陦ｨ
- [ ] 髮・・ MPAndroidChart 謌・Compose Charts
- [ ] 螳樒鴫雜句漢蝗ｾ・亥捉/譛・蟷ｴ・・
- [ ] 螳樒鴫扈溯ｮ｡謨ｰ謐ｮ螻慕､ｺ・域ｻ谺｡謨ｰ縲∝ｹｳ蝮・｢醍紫遲会ｼ・

#### 莉ｻ蜉｡ 18・壽焚謐ｮ蟇ｼ蜃ｺ/蟇ｼ蜈･螟・ｻｽ
- [ ] 螳樒鴫 JSON 蟇ｼ蜃ｺ蜉溯・
- [ ] 螳樒鴫 JSON 蟇ｼ蜈･諱｢螟榊粥閭ｽ
- [ ] 豺ｻ蜉譁・ｻｶ騾画叫蝎ｨ UI

---

## Phase 3・壼｢槫ｼｺ莨伜喧・磯｢・ｮ｡ 2-3 蜻ｨ・・

### 逶ｮ譬・
TFLite 蜿ｯ騾画ｨ｡蝙九∬ｿ懃ｨ句､・ｻｽ謗･蜿｣鬚・蕗縲ゞI 鄒主喧縲∵ｧ閭ｽ莨伜喧縲∝黒蜈・ｵ玖ｯ輔・

### 莉ｻ蜉｡蛻苓｡ｨ

#### 莉ｻ蜉｡ 19・啜FLite 髮・・
- [ ] 豺ｻ蜉 TensorFlow Lite 萓晁ｵ・
- [ ] 螳樒鴫 TFLite 譽豬句ｼ墓梼・亥ｮ樒鴫 DetectionEngine 謗･蜿｣・・
- [ ] 鬚・ｮｭ扈・ｨ｡蝙狗ｮ｡逅・ｼ井ｸ玖ｽｽ/郛灘ｭ假ｼ・
- [ ] 隶ｩ逕ｨ謌ｷ蝨ｨ莨扈溽ｮ玲ｳ募柱 TFLite 荵矩龍蛻・困

#### 莉ｻ蜉｡ 20・夊ｿ懃ｨ句､・ｻｽ謗･蜿｣
- [ ] 隶ｾ隶｡螟・ｻｽ謗･蜿｣謚ｽ雎｡
- [ ] 鬚・蕗 WebDAV / 閾ｪ螳壻ｹ・API 謇ｩ螻慕せ
- [ ] 螳樒鴫蝓ｺ遑逧・ｽ醍ｻ懷､・ｻｽ UI

#### 莉ｻ蜉｡ 21・啅I 鄒主喧
- [ ] 隶ｾ隶｡蟷ｶ蠎皮畑 Material You 荳ｻ鬚倩牡
- [ ] 豺ｻ蜉霑・ｸ｡蜉ｨ逕ｻ
- [ ] 莨伜喧荳榊酔螻丞ｹ募ｰｺ蟇ｸ逧・る・

#### 莉ｻ蜉｡ 22・壽ｧ閭ｽ莨伜喧
- [ ] 逶ｸ譛ｺ蛻・ｾｨ邇・蟶ｧ邇・剄郤ｧ騾蛾｡ｹ
- [ ] 菴守ｫｯ隶ｾ螟・る・
- [ ] 逕ｵ豎莨伜喧・亥錘蜿ｰ髯仙宛・・

#### 莉ｻ蜉｡ 23・壼黒蜈・ｵ玖ｯ・
- [ ] 荳ｺ Repository 螻らｼ門・蜊募・豬玖ｯ・
- [ ] 荳ｺ譽豬句ｼ墓梼郛門・蜊募・豬玖ｯ・
- [ ] 荳ｺ ViewModel 郛門・蜊募・豬玖ｯ・
- [ ] 髮・・豬玖ｯ包ｼ・I 豬∫ｨ具ｼ・

---

## 蜈ｳ髞ｮ髫ｾ轤ｹ螳樒鴫譁ｹ譯・

### 1. ImageProxy 霓ｬ OpenCV Mat
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

### 2. 蟶ｧ蟾ｮ蛻・ｳｰ蛟ｼ譽豬・
- 菴ｿ逕ｨ貊大勘遯怜哨隶｡邂苓ｿ仙勘蠑ｺ蠎ｦ
- 蠎皮畑菴朱壽ｻ､豕｢蟷ｳ貊大飭螢ｰ
- 譽豬句ｱ驛ｨ蟲ｰ蛟ｼ・御ｽｿ逕ｨ譛蟆城龍髫碑ｿ・ｻ､・・ebounce・・

### 3. 螟夂畑謌ｷ謨ｰ謐ｮ髫皮ｦｻ
- 謇譛・DAO 譟･隸｢豺ｻ蜉 userId WHERE 譚｡莉ｶ
- Repository 螻らｻ滉ｸ霑・ｻ､
- 蛻・困逕ｨ謌ｷ譌ｶ貂・ｩｺ蜀・ｭ倡ｼ灘ｭ・

---

## 豬玖ｯ慕ｭ也払

### 蜊募・豬玖ｯ・
- **Repository 螻・*・壻ｽｿ逕ｨ Room in-memory 謨ｰ謐ｮ蠎捺ｵ玖ｯ墓園譛・CRUD 謫堺ｽ・
- **譽豬句ｼ墓梼**・壻ｽｿ逕ｨ讓｡諡溽噪蟶ｧ/髻ｳ鬚第焚謐ｮ豬玖ｯ慕ｮ玲ｳ暮ｻ霎・
- **ViewModel**・壻ｽｿ逕ｨ kotlinx-coroutines-test 豬玖ｯ慕憾諤∫ｮ｡逅・

### 髮・・豬玖ｯ・
- **UI 豬∫ｨ・*・壻ｽｿ逕ｨ Compose UI Test 豬玖ｯ戊ｮ｡謨ｰ豬∫ｨ・
- **逶ｸ譛ｺ髮・・**・壻ｽｿ逕ｨ CameraX 豬玖ｯ募ｷ･蜈ｷ鬪瑚ｯ・｢・ｧ亥柱蟶ｧ蛻・梵

### 謇句勘豬玖ｯ・
- **荳榊酔蜈臥・譚｡莉ｶ**・壼ｮ､蜀・∝ｮ､螟悶・・・
- **荳榊酔隶ｾ螟・*・夐ｫ倡ｫｯ/荳ｭ遶ｯ/菴守ｫｯ隶ｾ螟・
- **髟ｿ譌ｶ髣ｴ霑占｡・*・・蟆乗慮莉･荳顔噪遞ｳ螳壽ｧ豬玖ｯ・

---

## 鬪梧噺譬・㊥

### Phase 1 鬪梧噺譬・㊥
- [ ] 蠎皮畑蜿ｯ莉･豁｣蟶ｸ蜷ｯ蜉ｨ蟷ｶ譏ｾ遉ｺ隶｡謨ｰ逡碁擇
- [ ] 鞫・ワ螟ｴ鬚・ｧ域ｭ｣蟶ｸ譏ｾ遉ｺ
- [ ] 諡肴焔蜉ｨ菴懷庄莉･陲ｫ豁｣遑ｮ隶｡謨ｰ・亥㊥遑ｮ邇・> 80%・・
- [ ] 隶｡謨ｰ扈捺棡蜿ｯ莉･菫晏ｭ伜芦譛ｬ蝨ｰ謨ｰ謐ｮ蠎・
- [ ] 蜴・彰隶ｰ蠖暮｡ｵ髱｢蜿ｯ莉･譟･逵句ｷｲ菫晏ｭ倡噪莨夊ｯ・
- [ ] 蠎皮畑蜿ｯ莉･遞ｳ螳夊ｿ占｡・30 蛻・帖莉･荳贋ｸ榊ｴｩ貅・

### Phase 2 鬪梧噺譬・㊥
- [ ] 謾ｯ謖∝､夂畑謌ｷ蛻・困
- [ ] 髻ｳ鬚第｣豬句庄莉･豁｣遑ｮ隶｡謨ｰ・域牛謇・霍ｳ扈ｳ螢ｰ髻ｳ・・
- [ ] 逕ｨ謌ｷ蜿ｯ莉･蠖募宛閾ｪ螳壻ｹ画ｷ譛ｬ蟷ｶ逕ｨ莠手ｮ｡謨ｰ
- [ ] 髦亥ｼ隹・鰍螳樊慮逕滓譜
- [ ] 謨ｰ謐ｮ蛻・梵鬘ｵ髱｢螻慕､ｺ豁｣遑ｮ逧・ｻ溯ｮ｡蝗ｾ陦ｨ
- [ ] 謨ｰ謐ｮ蜿ｯ莉･蟇ｼ蜃ｺ荳ｺ JSON 蟷ｶ蟇ｼ蜈･諱｢螟・

### Phase 3 鬪梧噺譬・㊥
- [ ] TFLite 讓｡蝙句庄騾牙ｹｶ謠宣ｫ伜､肴揩蜉ｨ菴懆ｯ・悪邇・
- [ ] 霑懃ｨ句､・ｻｽ謗･蜿｣鬚・蕗螳梧・
- [ ] UI 蝨ｨ謇譛臥岼譬・ｮｾ螟・ｸ頑仞遉ｺ豁｣蟶ｸ
- [ ] 菴守ｫｯ隶ｾ螟・ｿ占｡梧ｵ∫腐・亥ｸｧ邇・> 15fps・・
- [ ] 蜊募・豬玖ｯ戊ｦ・尠邇・> 60%
