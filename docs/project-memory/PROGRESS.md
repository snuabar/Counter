# Counter App 项目进度跟踪

> **用途：** 本文件用于在多台开发电脑之间同步项目状态。当在新电脑上打开项目时，首先阅读此文件以了解当前进度。
>
> **最后更新：** 2026-06-25（会话3）

> ⚠️ **部分功能待验证：** 音频检测引擎、模板匹配算法尚未在真机上充分测试。TFLite 姿态检测、动作识别、前后摄像头骨架显示已通过真机验证。

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
| **相机** | Camera2（计数） + CameraX（模板录制） |
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
│   │   │       ├── CustomPoseActionDetector.kt # 自定义模板（占位）
│   │   │       └── ActionDetectorFactory.kt
│   │   └── vision/
│   │       ├── FrameDifferencer.kt
│   │       └── MotionDetector.kt
│   ├── template/            # 模板匹配（骨架）
│   │   ├── TemplateMatcher.kt
│   │   ├── VisionTemplateMatcher.kt
│   │   └── AudioTemplateMatcher.kt
│   ├── biometric/
│   │   └── BiometricAuthManager.kt
│   └── service/              # 后台服务
│       ├── TimerService.kt              # 计时前台服务
│       └── TimerStateHolder.kt          # 计时状态共享
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
│   │   ├── ActionType.kt              # PUSH_UP, SQUAT, CUSTOM
│   │   ├── User.kt
│   │   ├── CountingSession.kt
│   │   ├── CountEvent.kt
│   │   ├── Template.kt                # 含 actionType 字段
│   │   ├── SessionMode.kt             # COUNTING, TIMER
│   │   ├── SensorType.kt              # VISION, AUDIO（唯一）
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
| 12 | 内置模板初始化 | ✅ | 俯卧撑（计数）、深蹲（计数）、平板支撑（计时） |

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
| 24 | 模板驱动动作选择 | ✅ | 选择模板自动确定动作类型，移除硬编码 ActionTypeSelector |
| 25 | 模型切换 UI | ✅ | SettingsScreen RadioButton 选择 3 个模型 |
| 26 | 远程备份接口预留 | ✅ | RemoteBackupDataSource + WebDAV 骨架 |
| 27 | UI 美化（动画 + 震动） | ✅ | animateContentSize, graphicsLayer, Vibrator |
| 28 | 性能优化（相机分辨率） | ✅ | targetResolution 配置 |
| 29 | Camera2 管线优化 | ✅ | 帧率5→10fps，骨架叠加层，模型热重载，前置摄像头方向修复 |
| 30 | 架构重构 | ✅ | ActionType→domain.model，SensorType统一，Template+actionType，DB v3 |
| 31 | TimerService 崩溃修复 | ✅ | Android 12+ 前台服务 5 秒规则修复 |
| 32 | 模板选择交互优化 | ✅ | 模板必选，自动选择首个，目标值可调 |
| 33 | 预览组件统一 | ✅ | PoseCameraPreview 统一封装 Camera2 预览 + 骨架绘制 + 摄像头切换 + 权限检查 |
| 34 | 摄像头切换标配化 | ✅ | CountingScreen 和 TemplateScreen 共用同一套摄像头切换 UI |
| 35 | 权限内聚化 | ✅ | 相机权限检查移入 PoseCameraPreview，Screen 无需关心权限逻辑 |

---

## 待实现功能（按难度排序）

### 🔴 高优先级（核心功能缺失）

| # | 功能 | 难度 | 说明 | 预计工作量 |
|---|------|------|------|----------|
| 1 | **自定义样本录制与特征提取** | ⭐⭐ 中等 | 录制界面已有入口，需实现特征提取（视觉：光流直方图；音频：MFCC）和保存 | 4小时 |
| 2 | **模板匹配集成** | ⭐⭐ 中等 | `VisionTemplateMatcher` / `AudioTemplateMatcher` 算法骨架已有，需与检测引擎集成 | 3小时 |
| 3 | **TFLite 真机调优** | ⭐⭐ 中等 | 基础骨架和角度阈值已真机验证，需进一步优化计数准确率，自定义模板匹配逻辑待实现 | 2小时 |

### 🟡 中优先级（功能增强）

| # | 功能 | 难度 | 说明 | 预计工作量 |
|---|------|------|------|----------|
| 4 | **音频检测引擎调优** | ⭐⭐ 中等 | `AudioDetectionEngine` 已有基础实现，需真机调优 | 2小时 |
| 5 | **计时反馈增强** | ⭐⭐ 中等 | 目标时间提醒（震动+音效）、中途语音播报（可开关） | 3小时 |
| 6 | **网络备份 UI** | ⭐⭐ 中等 | WebDAV 已实现，需添加配置和触发 UI | 2小时 |
| 7 | **实时阈值持久化** | ⭐ 简单 | 设置页滑块已有，需保存到数据库并实时应用到检测引擎 | 1小时 |

### 🟢 低优先级（锦上添花）

| # | 功能 | 难度 | 说明 | 预计工作量 |
|---|------|------|------|----------|
| 8 | **成就/庆祝系统** | ⭐⭐⭐⭐ 困难 | 完成目标时的动画 + 音效 + 震动反馈 | 4小时 |
| 9 | **多屏幕尺寸适配** | ⭐⭐ 中等 | 平板/折叠屏适配优化 | 3小时 |
| 10 | **低端设备性能优化** | ⭐⭐⭐ 较难 | 相机分辨率动态降级、帧率控制、电池优化 | 4小时 |
| 11 | **单元测试覆盖** | ⭐⭐⭐ 较难 | 已有 DetectionConfigTest，需补 Repository / Engine / ViewModel 测试 | 4小时 |
| 12 | **集成测试** | ⭐⭐⭐⭐ 困难 | Compose UI Test + CameraX 测试工具 | 8小时 |

---

## 关键决策记录

| 日期 | 决策 | 说明 |
|------|------|------|
| 2026-06-24 | TFLite 模型精简为 3 个 | 从 6 个 MoveNet 模型精简为 lightning-int8（快速）、lightning-fp16（标准）、thunder-fp16（精确） |
| 2026-06-24 | 动作识别基于规则（非 ML） | 俯卧撑/深蹲/平板支撑使用角度阈值判断，而非训练 ML 模型 |
| 2026-06-25 | 自定义模板预留接口 | 创建 `CustomPoseActionDetector` 占位，后续用 DTW 算法实现模板匹配 |
| 2026-06-25 | ActionType 整合到模板 | ActionType 移至 domain.model，Template 新增 actionType 字段，选择模板自动确定动作类型 |
| 2026-06-25 | PLANK 从 ActionType 移除 | 计时型活动通过 SessionMode.TIMER + 内置模板实现，不占用 ActionType |
| 2026-06-25 | 模板为必选项 | 移除"不使用模板"选项，自动选择第一个模板，未选模板时禁用开始按钮 |
| 2026-06-25 | Camera2 替代 CameraX 用于计数 | 计数界面改用 Camera2 实现更高帧率和更精确控制，模板录制仍用 CameraX |
| 2026-06-25 | PoseCameraPreview 统一组件 | 将 Camera2 预览、骨架绘制、摄像头切换、权限检查统一封装到单一组件，CountingScreen 和 TemplateScreen 复用 |
| 2026-06-25 | 摄像头切换作为预览标配 | 所有使用相机预览的页面统一支持摄像头切换（下拉菜单/单按钮），无需重复实现 |

---

## 重要文件速查

| 文件路径 | 说明 |
|---------|------|
| `app/src/main/assets/single_pose_detection/` | TFLite 模型存放目录（3 个 .tflite 文件） |
| `core/detection/tflite/PoseModelConfig.kt` | 模型配置枚举 |
| `core/detection/tflite/TFLiteDetectionEngine.kt` | TFLite 主引擎 |
| `ui/screen/counting/CountingScreen.kt` | 主计数界面 |
| `ui/screen/counting/CountingViewModel.kt` | 计数界面 ViewModel（已集成 TimerService） |
| `core/service/TimerService.kt` | 后台计时服务（平板支撑等） |
| `core/service/TimerStateHolder.kt` | 计时状态共享桥接 |
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
| 3 | ~~前置摄像头骨架方向错误~~ | ✅ 已修复 | 使用硬件 LENS_FACING 判断，前置摄像头 screenY=(1-x)*H |
| 4 | ~~TimerService 前台服务崩溃~~ | ✅ 已修复 | onStartCommand 开头统一调用 startForeground() 满足 Android 12+ 5秒规则 |
| 5 | ~~架构设计 ActionType/Template 重复~~ | ✅ 已修复 | ActionType 整合到 Template，移除硬编码选择器 |
| 6 | `ImageAnalysis.Builder.setTargetResolution()` 已弃用 | 编译警告 | 不影响功能，后续可迁移到 `setResolutionSelector()` |
| 7 | PoseCameraPreview x轴镜像逻辑冗余 | 功能正常 | `toScreen` 中 x 轴两个分支结果相同，待确认是否简化 |

---

## 下一步建议

**核心功能补全（让 App 真正可用）：**
1. TFLite 真机验证角度阈值（俯卧撑/深蹲计数准确率）
2. 模板匹配集成（自定义模板录制 → 特征提取 → 匹配检测）
3. 音频检测引擎真机调优

**功能增强：**
4. 计时反馈增强（目标时间提醒、语音播报）
5. 网络备份 UI（WebDAV 配置界面）
6. 实时阈值持久化

**质量保障：**
7. 单元测试覆盖（Repository / Engine / ViewModel）
8. 低端设备性能优化
