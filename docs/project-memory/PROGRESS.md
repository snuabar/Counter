# Counter App 项目进度跟踪

> **用途：** 本文件用于在多台开发电脑之间同步项目状态。当在新电脑上打开项目时，首先阅读此文件以了解当前进度。
>
> **最后更新：** 2026-06-25

> ⚠️ **重要提示：** 所有已实现功能均为**代码层面实现**，尚未在真机上进行测试验证。迁移后建议在真机上逐一验证核心功能（尤其是 TFLite 姿态检测和动作识别）。
>
> 已知仅在代码层面完成、未经验证的功能包括：TFLite 模型推理、动作识别角度阈值、音频检测引擎、模板匹配算法等。

---

## 项目简介

**项目名称：** Counter（智能计数器）
**包名：** `com.snuabar.counter`
**目标：** 构建一个安卓端智能计数应用，通过摄像头/麦克风捕获重复动作/声音自动计数，支持内置模板和自定义样本。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| **UI** | Jetpack Compose + Material3 |
| **架构** | MVVM + Clean Architecture + Repository 模式 |
| **依赖注入** | Hilt |
| **本地存储** | Room (SQLite) |
| **相机** | CameraX |
| **图像处理** | OpenCV + TensorFlow Lite (MoveNet) |
| **音频处理** | AudioRecord + FFT |
| **图表** | Compose 自绘（柱状图） |
| **构建** | Gradle with Kotlin DSL |

---

## 项目架构

```
app/src/main/java/com/snuabar/counter/
├── core/
│   ├── detection/           # 检测引擎核心
│   │   ├── DetectionEngine.kt
│   │   ├── DetectionEngineFactoryImpl.kt
│   │   ├── VisionDetectionEngine.kt          # 传统帧差分检测
│   │   ├── audio/
│   │   │   └── AudioDetectionEngine.kt       # 音频检测（FFT）
│   │   ├── tflite/
│   │   │   ├── PoseModelConfig.kt             # 模型配置（3个档位）
│   │   │   ├── TFLiteDetectionEngine.kt       # TFLite 主引擎
│   │   │   └── action/                        # 动作识别算法
│   │   │       ├── PoseActionResult.kt
│   │   │       ├── PoseActionDetector.kt
│   │   │       ├── BasePoseActionDetector.kt
│   │   │       ├── PushUpActionDetector.kt    # 俯卧撑
│   │   │       ├── SquatActionDetector.kt     # 深蹲
│   │   │       ├── PlankActionDetector.kt     # 平板支撑
│   │   │       ├── CustomPoseActionDetector.kt # 自定义模板（占位）
│   │   │       └── ActionDetectorFactory.kt
│   │   └── vision/
│   │       ├── FrameDifferencer.kt
│   │       └── MotionDetector.kt
│   ├── template/            # 模板匹配（骨架）
│   │   ├── TemplateMatcher.kt
│   │   ├── VisionTemplateMatcher.kt
│   │   └── AudioTemplateMatcher.kt
│   └── biometric/
│       └── BiometricAuthManager.kt
├── data/
│   ├── local/
│   │   ├── db/              # Room 数据库
│   │   │   ├── CounterDatabase.kt
│   │   │   ├── dao/         # UserDao, CountingSessionDao, CountEventDao, TemplateDao
│   │   │   └── entity/      # UserEntity, CountingSessionEntity, CountEventEntity, TemplateEntity
│   │   └── prefs/           # DataStore 偏好
│   │       ├── ThemePreferences.kt
│   │       └── UserPreferences.kt
│   ├── remote/              # 远程备份
│   │   ├── RemoteBackupDataSource.kt
│   │   └── WebDAVRemoteBackupDataSource.kt
│   └── repository/          # Repository 实现
│       ├── BackupRepository.kt          # JSON 导入/导出
│       ├── CountingSessionRepositoryImpl.kt
│       ├── TemplateRepositoryImpl.kt
│       └── UserRepositoryImpl.kt
├── domain/
│   ├── model/               # 领域模型
│   │   ├── User.kt
│   │   ├── CountingSession.kt
│   │   ├── CountEvent.kt
│   │   ├── Template.kt
│   │   ├── SessionMode.kt
│   │   └── SessionStatistics.kt
│   └── repository/          # Repository 接口
│       ├── CountingSessionRepository.kt
│       ├── TemplateRepository.kt
│       └── UserRepository.kt
├── di/                      # Hilt 模块
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   └── RepositoryModule.kt
└── ui/
    ├── navigation/
    │   └── Navigation.kt              # 底部导航（5个Tab）
    └── screen/
        ├── counting/
        │   ├── CountingScreen.kt        # 主计数界面
        │   └── CountingViewModel.kt
        ├── history/
        │   ├── HistoryScreen.kt         # 历史记录
        │   └── HistoryViewModel.kt
        ├── analysis/
        │   ├── AnalysisScreen.kt        # 数据分析（柱状图）
        │   └── AnalysisViewModel.kt
        ├── template/
        │   ├── TemplateScreen.kt        # 模板管理
        │   └── TemplateViewModel.kt
        ├── settings/
        │   ├── SettingsScreen.kt        # 设置
        │   └── SettingsViewModel.kt
        └── user/
            ├── UserScreen.kt            # 用户管理
            └── UserViewModel.kt
```

---

## 已实现功能

### Phase 1：MVP（核心基础）

| # | 功能 | 状态 | 备注 |
|---|------|------|------|
| 1 | 项目初始化（build.gradle.kts, AndroidManifest, Hilt） | ✅ | 已完成 |
| 2 | Clean Architecture 目录结构 | ✅ | 已完成 |
| 3 | Domain 层数据模型 | ✅ | User, CountingSession, CountEvent, Template |
| 4 | Room 数据库（Entity, DAO, Database） | ✅ | 已完成 |
| 5 | Repository 层（接口 + 实现） | ✅ | 已完成 |
| 6 | Hilt DI 配置 | ✅ | AppModule, DatabaseModule, RepositoryModule |
| 7 | 视觉检测引擎（帧差分） | ✅ | VisionDetectionEngine |
| 8 | 音频检测引擎（FFT） | ✅ | AudioDetectionEngine（基础实现） |
| 9 | TFLite 检测引擎 | ✅ | 支持 3 个 MoveNet 模型（192/256 输入） |
| 10 | 基础 Compose UI（计数界面） | ✅ | CountingScreen + CountingViewModel |
| 11 | 底部导航 + 5 个 Tab | ✅ | 计数、历史、分析、模板、设置 |
| 12 | 内置模板初始化 | ✅ | 拍手、跳绳、平板支撑 |

### Phase 2：完整功能

| # | 功能 | 状态 | 备注 |
|---|------|------|------|
| 13 | 多用户支持 | ✅ | 创建、切换、删除、默认用户 |
| 14 | 用户管理 UI | ✅ | UserScreen |
| 15 | 历史记录页面 | ✅ | HistoryScreen + 会话列表 |
| 16 | 数据分析页面 | ✅ | AnalysisScreen + 柱状图 + 统计卡片 |
| 17 | 模板管理页面 | ✅ | TemplateScreen + 添加模板对话框 |
| 18 | 阈值调节 | ✅ | SettingsScreen 滑块（0.1~1.0） |
| 19 | 深色模式支持 | ✅ | ThemePreferences + 3种模式（系统/浅色/深色） |
| 20 | 生物识别切换用户 | ✅ | BiometricAuthManager + UserScreen |
| 21 | 数据导出/导入（JSON） | ✅ | BackupRepository + SettingsScreen UI |

### Phase 3：增强优化（已完成部分）

| # | 功能 | 状态 | 备注 |
|---|------|------|------|
| 22 | TFLite 集成（MoveNet） | ✅ | lightning-int8, lightning-fp16, thunder-fp16 |
| 23 | 3 个内置动作识别 | ✅ | 俯卧撑、深蹲、平板支撑（基于关键点角度） |
| 24 | 动作类型选择 UI | ✅ | CountingScreen 中 FilterChip 选择 |
| 25 | 模型切换 UI | ✅ | SettingsScreen RadioButton 选择 3 个模型 |
| 26 | 远程备份接口预留 | ✅ | RemoteBackupDataSource + WebDAV 骨架 |
| 27 | UI 美化（动画 + 震动） | ✅ | animateContentSize, graphicsLayer, Vibrator |
| 28 | 性能优化（相机分辨率） | ✅ | targetResolution 配置 |

---

## 待实现功能（按难度排序）

### 🔴 高优先级（简单，影响可用性）

| # | 功能 | 难度 | 说明 | 预计工作量 |
|---|------|------|------|-----------|
| 1 | **~~会话保存到数据库~~** | ✅ 已完成 | `CountingViewModel.stopCounting()` 已调用 `sessionRepository.createSession()` | 30分钟 |
| 2 | **~~SettingsScreen "其他设置"占位文案更新~~** | ✅ 已完成 | 已改为"关于"版本信息卡片 | 5分钟 |
| 3 | **~~模板删除功能~~** | ✅ 已完成 | 自定义模板可删除，内置模板无删除按钮 | 15分钟 |
| 4 | **~~计数时实时保存~~** | ✅ 已完成 | `startCounting()` 创建 RUNNING session，每次计数变化实时更新 `finalCount` | 30分钟 |

### 🟡 中优先级（中等，核心功能）

| # | 功能 | 难度 | 说明 | 预计工作量 |
|---|------|------|------|-----------|
| 5 | **TFLite 真机验证** | ⭐⭐ 中等 | 当前角度阈值基于理论值，需在真机上验证俯卧撑/深蹲/平板支撑的实际检测准确率 | 2小时 |
| 6 | **模板匹配算法** | ⭐⭐ 中等 | `VisionTemplateMatcher` / `AudioTemplateMatcher` 为占位实现，需实现余弦相似度/欧氏距离/DTW | 4小时 |
| 7 | **自定义模板录制** | ⭐⭐ 中等 | UI 已存在，但无实际录制逻辑。需实现：录制关键点序列 → 保存为模板 → 计数时匹配 | 6小时 |
| 8 | **音频检测引擎调优** | ⭐⭐ 中等 | `AudioDetectionEngine` 使用简单 DFT，频域范围和阈值可能需要针对拍手/跳绳调优 | 3小时 |
| 9 | **分析页面图表增强** | ⭐⭐ 中等 | 当前为 Compose 自绘柱状图，可追加折线图、周/月/年切换、更丰富的统计数据 | 4小时 |

### 🟢 低优先级（较难，锦上添花）

| # | 功能 | 难度 | 说明 | 预计工作量 |
|---|------|------|------|-----------|
| 10 | **单元测试覆盖** | ⭐⭐⭐ 较难 | 仅 1 个测试文件，需补 Repository / Engine / ViewModel 测试 | 6小时 |
| 11 | **性能优化（低端机）** | ⭐⭐⭐ 较难 | 相机分辨率动态降级、帧率控制、内存优化、电池优化 | 4小时 |
| 12 | **后台持续计时** | ⭐⭐⭐ 较难 | 平板支撑计时模式需后台 Service，锁屏或切出 App 继续计时 | 4小时 |
| 13 | **语音播报** | ⭐⭐⭐ 较难 | 目标时间提醒、每 10 个播报一次、完成提醒 | 3小时 |
| 14 | **成就/庆祝系统** | ⭐⭐⭐⭐ 困难 | 完成目标时的动画 + 音效 + 震动反馈 | 4小时 |
| 15 | **远程备份实现** | ⭐⭐⭐⭐ 困难 | WebDAVRemoteBackupDataSource 为骨架，需实现实际的 WebDAV 上传/下载 | 6小时 |
| 16 | **集成测试** | ⭐⭐⭐⭐ 困难 | Compose UI Test + CameraX 测试工具 | 8小时 |

---

## 关键决策记录

| 日期 | 决策 | 说明 |
|------|------|------|
| 2026-06-24 | TFLite 模型精简为 3 个 | 从 6 个 MoveNet 模型精简为 lightning-int8（快速）、lightning-fp16（标准）、thunder-fp16（精确） |
| 2026-06-24 | 动作识别基于规则（非 ML） | 俯卧撑/深蹲/平板支撑使用角度阈值判断，而非训练 ML 模型 |
| 2026-06-25 | 自定义模板预留接口 | 创建 `CustomPoseActionDetector` 占位，后续用 DTW 算法实现模板匹配 |

---

## 重要文件速查

| 文件路径 | 说明 |
|---------|------|
| `app/src/main/assets/single_pose_detection/` | TFLite 模型存放目录（3 个 .tflite 文件） |
| `core/detection/tflite/PoseModelConfig.kt` | 模型配置枚举 |
| `core/detection/tflite/TFLiteDetectionEngine.kt` | TFLite 主引擎 |
| `ui/screen/counting/CountingScreen.kt` | 主计数界面 |
| `ui/screen/counting/CountingViewModel.kt` | **待完善：会话保存** |
| `data/repository/BackupRepository.kt` | 导出/导入逻辑 |
| `docs/superpowers/specs/2026-06-24-counter-app-plan.md` | 详细实现计划 |
| `docs/project-memory/PROGRESS.md` | **本文件：项目进度** |

---

## 迁移到新电脑的步骤

1. **克隆 Git 仓库**
   ```bash
   git clone <repo-url>
   cd Counter
   ```

2. **在 Android Studio 中打开项目**
   - 首次打开时会自动下载 Gradle wrapper 和依赖
   - 如果遇到 TFLite 依赖下载失败，请确保网络通畅或配置国内镜像

3. **检查 TFLite 模型文件**
   - 确认 `app/src/main/assets/single_pose_detection/` 下有 3 个 .tflite 文件
   - 如果缺失，需要从 [MoveNet TFLite Models](https://www.tensorflow.org/lite/models/pose_estimation/overview) 下载

4. **编译验证**
   ```bash
   ./gradlew :app:compileDebugKotlin
   ```

5. **阅读本文件了解进度**
   - 查看"待实现功能"列表，决定下一步工作

---

## 已知问题

| # | 问题 | 影响 | 解决方案 |
|---|------|------|---------|
| 1 | ~~`CountingViewModel` 不保存会话~~ | ✅ 已修复 | `startCounting()` 创建 RUNNING session，`stopCounting()` 更新为 COMPLETED |
| 2 | ~~SettingsScreen "其他设置"占位文案~~ | ✅ 已修复 | 改为"关于"版本信息卡片 |
| 3 | `FilterChip` 是实验性 API | 编译警告 | 已添加 `@OptIn(ExperimentalMaterial3Api::class)` |
| 4 | `ImageAnalysis.Builder.setTargetResolution()` 已弃用 | 编译警告 | 不影响功能，后续可迁移到 `setResolutionSelector()` |

---

## 下一步建议

**如果希望尽快让 App 可用：**
1. ✅ ~~实现会话保存~~（已完成）
2. ✅ ~~修复 SettingsScreen 占位文案~~（已完成）
3. TFLite 真机验证角度阈值

**如果希望增强功能：**
4. 自定义模板录制 + 匹配
5. 分析页面图表增强
6. 后台持续计时 Service
