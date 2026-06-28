# Counter App 项目进度跟踪

> **用途：** 本文件用于在多台开发电脑之间同步项目状态。当在新电脑上打开项目时，首先阅读此文件以了解当前进度。
>
> **最后更新：** 2026-06-28（会话5）

> **状态总览：**
> - Phase 1（MVP）：100% 完成
> - Phase 2（完整功能）：~90% 完成
> - Phase 3（增强优化）：~85% 完成

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
| **本地存储** | Room (SQLite) + DataStore |
| **相机** | Camera2（预览 + 分析） |
| **姿态检测** | MediaPipe Tasks Vision (PoseLandmarker) |
| **音频处理** | AudioRecord + FFT |
| **图表** | Compose 自绘（柱状图） |
| **构建** | Gradle with Kotlin DSL |

---

## 已实现功能（真机验证通过）

### Phase 1：MVP（核心基础）

| # | 功能 | 状态 |
|---|------|------|
| 1 | 项目初始化（build.gradle.kts, AndroidManifest, Hilt） | 完成 |
| 2 | Clean Architecture 目录结构 | 完成 |
| 3 | Domain 层数据模型（User, CountingSession, CountEvent, Template） | 完成 |
| 4 | Room 数据库（Entity, DAO, Database） | 完成 |
| 5 | Repository 层（接口 + 实现） | 完成 |
| 6 | Hilt DI 配置 | 完成 |
| 7 | 基础 Compose UI（计数界面） | 完成 |
| 8 | 底部导航 + 5 个 Tab | 完成 |

### Phase 2：完整功能

| # | 功能 | 状态 |
|---|------|------|
| 9 | 多用户支持（创建、切换、删除） | 完成 |
| 10 | 用户管理 UI | 完成 |
| 11 | 历史记录页面 | 完成 |
| 12 | 数据分析页面（柱状图 + 统计卡片） | 完成 |
| 13 | 模板管理页面（添加/删除/列表） | 完成 |
| 14 | 阈值调节（SettingsScreen 滑块 0.1~1.0） | 完成 |
| 15 | 深色模式支持（系统/浅色/深色） | 完成 |
| 16 | 生物识别切换用户 | 完成 |
| 17 | 数据导出/导入（JSON） | 完成 |

### Phase 3：增强优化

| # | 功能 | 状态 |
|---|------|------|
| 18 | MediaPipe PoseLandmarker 迁移（lite/full/heavy 三档） | 完成 |
| 19 | GPU 加速自动回退（推理速度提升 3 倍+） | 完成 |
| 20 | FPS 实时显示（PoseCameraPreview 左上角） | 完成 |
| 21 | 推理异步化（analysisExecutor 异步执行） | 完成 |
| 22 | 帧率限流（15fps） | 完成 |
| 23 | 骨架方向修复（前后置旋转 + 镜像） | 完成 |
| 24 | 摄像头切换标配化（下拉菜单/单按钮） | 完成 |
| 25 | PoseCameraPreview 统一组件 | 完成 |
| 26 | 内置模板初始化（俯卧撑、深蹲、平板支撑） | 完成 |
| 27 | 动作识别基于规则（角度阈值） | 完成 |
| 28 | 模板录制流程（倒计时 + 特征提取 + 最佳片段 + 质量检查） | 完成 |
| 29 | 自定义模板匹配算法（CustomPoseActionDetector + DTW） | 完成 |
| 30 | 模板匹配器骨架（VisionTemplateMatcher / AudioTemplateMatcher） | 完成 |
| 31 | 硬件加速开关（SettingsScreen GPU/CPU 切换） | 完成 |
| 32 | WebDAV 远程备份配置 UI | 完成 |
| 33 | TimerService 前台服务（平板支撑计时） | 完成 |
| 34 | 骨架刷新率提升（快速模式约 20-23fps） | 完成 |

---

## 待实现/待完善功能（按优先级排序）

### 高优先级（核心功能待联调）

| # | 功能 | 说明 | 预估工作量 |
|---|------|------|----------|
| 1 | **模板录制-计数联调** | TemplateScreen 录制流程、TemplateRecorder、CustomPoseActionDetector 均已实现，但尚未在真机验证完整流程：录制模板 → 保存到数据库 → 选择模板计数 → DTW 匹配计数 | 4小时 |
| 2 | ~~骨架刷新率提升至 20-23fps~~ | **已完成** — 快速模式 GPU 加速下约 20-23fps | - |
| 3 | **音频检测引擎真机调优** | AudioDetectionEngine 已有基础实现（FFT + 峰值检测 + 自适应噪声门限），但尚未在真机充分测试，阈值/参数需调优 | 3小时 |
| 4 | **计时反馈增强** | 目标时间到达提醒（震动+音效）、中途语音播报（可开关）、完成庆祝动画（可开关） | 4小时 |

### 中优先级（功能增强）

| # | 功能 | 说明 | 预估工作量 |
|---|------|------|----------|
| 5 | **网络备份完整实现** | WebDAV 配置 UI 已有，但上传/下载触发和实际备份流程待完善 | 3小时 |
| 6 | **低端设备性能优化** | 相机分辨率动态降级、帧率自适应、电池优化 | 4小时 |
| 7 | **多屏幕尺寸适配** | 平板/折叠屏布局适配 | 3小时 |

### 低优先级（质量保障）

| # | 功能 | 说明 | 预估工作量 |
|---|------|------|----------|
| 8 | **单元测试覆盖** | 仅 1 个测试文件，需补 Repository / Engine / ViewModel 测试 | 6小时 |
| 9 | **集成测试** | Compose UI Test + 相机集成测试 | 8小时 |

---

## 关键决策记录

| 日期 | 决策 | 说明 |
|------|------|------|
| 2026-06-24 | TFLite 模型精简为 3 个 | lightning-int8（快速）、lightning-fp16（标准）、thunder-fp16（精确） |
| 2026-06-24 | 动作识别基于规则（非 ML） | 俯卧撑/深蹲/平板支撑使用角度阈值判断 |
| 2026-06-25 | ActionType 整合到模板 | ActionType 移至 domain.model，Template 新增 actionType 字段 |
| 2026-06-25 | Camera2 替代 CameraX | 计数界面改用 Camera2 实现更高帧率和更精确控制 |
| 2026-06-25 | PoseCameraPreview 统一组件 | Camera2 预览 + 骨架绘制 + 摄像头切换 + 权限检查统一封装 |
| 2026-06-27 | MediaPipe PoseLandmarker 迁移 | 使用 `com.google.mediapipe:tasks-vision` 替代手动 TFLite 推理 |
| 2026-06-27 | GPU 优先自动回退 | 优先 GPU delegate，不支持时自动回退 CPU |
| 2026-06-27 | 摄像头旋转角度修复 | `rotationAngle = sensorOrientation.toFloat()` 替代 `(360 - sensorOrientation) % 360` |
| 2026-06-27 | 骨架刷新率优化（6项） | 见下方"性能优化详细记录" |

---

## 性能优化详细记录（2026-06-27）

**目标：** 骨架刷新率从 ~12fps 提升至 ~20-23fps（快速模式 GPU 加速）

| # | 优化项 | 文件 | 改动说明 | 效果 |
|---|--------|------|----------|------|
| 1 | YUV→Bitmap Buffer 拷贝 | `Camera2Preview.kt` | 先将 Buffer 数据拷贝到 `byte[]`，再用数组索引访问，避免每次循环的 JNI 调用 | 减少 JNI 开销 |
| 2 | Bitmap 旋转前移 | `Camera2Preview.kt` + `TFLiteDetectionEngine.kt` | 在 CameraThread 完成 Bitmap 旋转，TFLite 引擎不再旋转 | 减少推理线程工作量 |
| 3 | 调整线程模型 | `Camera2Preview.kt` | YUV→Bitmap 和旋转从 CameraThread 移到 AnalysisThread，避免阻塞相机回调 | 相机回调不阻塞 |
| 4 | 内存复用 | `TFLiteDetectionEngine.kt` | 复用 `previousKeypoints` 数组，避免每帧创建新数组 | 减少 GC 压力 |
| 5 | 移除同步锁 | `TFLiteDetectionEngine.kt` | 移除 `synchronized(this)`，减少锁竞争 | 降低线程阻塞 |
| 6 | MediaPipe VIDEO 模式 | `PoseLandmarkerHelper.kt` | 从 `IMAGE` 模式改为 `VIDEO` 模式，利用跨帧跟踪加速推理 | 推理加速 |

**结果：** 快速模式 GPU 加速下，骨架刷新率从 ~12fps 提升至 **20-23fps**。

---

## 已知问题

| # | 问题 | 状态 | 说明 |
|---|------|------|------|
| 1 | 前置摄像头骨架左右镜像 | 已修复 | `toScreen` 中恢复 `1f - kp[0]` 前置镜像逻辑 |
| 2 | 骨架 90 度旋转（头在左/右） | 已修复 | `rotationAngle = sensorOrientation.toFloat()` 正确旋转 Bitmap |
| 3 | `ImageAnalysis.Builder.setTargetResolution()` 已弃用 | 编译警告 | 不影响功能，后续可迁移到 `setResolutionSelector()` |
| 4 | Camera2Preview 未使用参数 | 编译警告 | `isFrontCamera` 和 `modifier` 参数在组件内未使用，功能正常 |

---

## 下一步建议

**当前会话遗留（会话5）：**
1. 模板录制-计数联调（验证录制→保存→选择→计数的完整流程）
2. 音频检测引擎真机调优

**近期可推进（让 App 更完整）：**
4. 计时反馈增强（目标提醒、语音播报、庆祝动画）
5. 网络备份完整实现（WebDAV 上传/下载）
6. 低端设备性能优化

**长期质量保障：**
7. 单元测试覆盖（Repository / Engine / ViewModel）
8. 多屏幕尺寸适配
