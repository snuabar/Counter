# Counter

一个基于 Android 的智能计数应用，通过摄像头捕获重复动作或声音自动计数。

## 核心功能

- **多种检测模式**：传统视觉算法（帧差分）、音频检测（FFT）、TFLite 姿态检测
- **内置动作识别**：俯卧撑、深蹲、平板支撑（基于关键点角度计算）
- **计时模式**：支持平板支撑等静态姿势计时
- **多用户管理**：支持创建、切换、删除用户
- **数据备份**：JSON 导入/导出
- **深色模式**：支持跟随系统/浅色/深色三种模式

## 技术栈

- **UI**：Jetpack Compose + Material3
- **架构**：MVVM + Clean Architecture + Hilt 依赖注入
- **数据库**：Room (SQLite)
- **图像处理**：OpenCV + TensorFlow Lite (MoveNet)
- **音频处理**：AudioRecord + FFT

## 项目文档

- `docs/project-memory/PROGRESS.md` — 项目进度跟踪（迁移到新电脑后首先阅读）
- `docs/superpowers/specs/2026-06-24-counter-app-plan.md` — 详细实现计划

## ⚠️ 重要提示

所有已实现功能均为**代码层面实现**，尚未在真机上进行测试验证。
