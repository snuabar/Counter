# Counter

一个基于 Android 的智能计数应用，通过摄像头捕获重复动作自动计数。

## 核心功能

- **姿态检测**：MediaPipe PoseLandmarker（33 个关键点），支持 lite/full/heavy 三档模型
- **GPU 加速**：优先 GPU delegate，不支持时自动回退 CPU，推理速度提升 3 倍+
- **内置动作识别**：俯卧撑、深蹲、平板支撑（基于关键点角度计算，规则引擎）
- **计时模式**：支持平板支撑等静态姿势计时
- **多用户管理**：支持创建、切换、删除用户
- **数据备份**：JSON 导入/导出，WebDAV 远程备份（骨架已预留）
- **深色模式**：支持跟随系统/浅色/深色三种模式
- **FPS 实时显示**：预览画面左上角叠加骨架刷新频率

## 技术栈

- **UI**：Jetpack Compose + Material3
- **架构**：MVVM + Clean Architecture + Hilt 依赖注入
- **数据库**：Room (SQLite)
- **姿态检测**：MediaPipe Tasks Vision (PoseLandmarker)
- **ML 推理加速**：TFLite GPU Delegate
- **音频处理**：AudioRecord + FFT
- **相机**：Camera2（预览 + 录制）

## 项目文档

| 文件 | 说明 |
|------|------|
| `docs/project-memory/PROGRESS.md` | 项目进度跟踪（迁移到新电脑后首先阅读） |
| `docs/superpowers/specs/2026-06-24-counter-app-design.md` | 设计规格说明书 |
| `docs/superpowers/specs/2026-06-24-counter-app-plan.md` | 详细实现计划 |
