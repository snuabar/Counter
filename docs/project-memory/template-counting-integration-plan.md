# 模板录制-计数联调计划

> **创建日期：** 2026-06-28 10:12:33
> **目标：** 打通"模板录制 → 保存 → 计数匹配"完整链路，确保端到端可用
> **状态：** 🟡 阶段1进行中

---

## 变更记录

| 时间 | 修改内容 | 文件 |
|------|----------|------|
| 2026-06-28 | 修复特征提取 fallback 不一致，统一 TemplateRecorder 和 CustomPoseActionDetector 的脚踝/手腕 fallback 逻辑 | `CustomPoseActionDetector.kt` |
| 2026-06-28 | 添加模板 featureVector 大小日志 | `TemplateViewModel.kt` |
| 2026-06-28 | 增强模板加载日志，添加 featureDim 和首帧特征值 | `CustomPoseActionDetector.kt` |

---

## 一、当前代码状态

**流程已实现，数据流已打通：**

```
模板录制：TemplateScreen → Camera2Preview → TemplateViewModel.processBitmap
  → TFLiteDetectionEngine → processKeypoints → RecordingSession
  → TemplateRecorder.addKeypoints → stopAndBuildTemplate → 编码为 ByteArray
  → 存入数据库 (Room)

计数匹配：CountingScreen → TFLiteDetectionEngine → processKeypoints
  → CustomPoseActionDetector.detect → 提取特征 → 滑动窗口
  → DTW 匹配模板 → 返回 CountEvent
```

---

## 二、发现的问题

| # | 问题 | 影响 | 修复优先级 |
|---|------|------|-----------|
| 1 | **特征提取 fallback 不一致** — TemplateRecorder 在脚踝/手腕不可见时回退到膝盖/肘，CustomPoseActionDetector 没有 | 录制和计数时同一帧 keypoints 提取的特征不同，DTW 匹配精度下降 | 🔴 高 |
| 2 | **VisionTemplateMatcher 未使用** — 实现了但未在代码中引用，实际用 CustomPoseActionDetector 内置 DTW | 冗余代码，可能造成困惑 | 🟡 中 |
| 3 | **DTW 匹配参数未经验证** — matchThreshold=0.7f, MIN_MOVEMENT_THRESHOLD=0.05f, MIN_PERIODICITY_SCORE=0.15f 等为经验值 | 实际匹配效果未知，可能过严或过松 | 🟡 中 |
| 4 | **模板录制完成后 feature vector 大小未验证** — 虽然 stopAndBuildTemplate 返回 null 时会有提示，但缺少 feature vector 大小的日志 | 不便于调试 | 🟢 低 |

---

## 三、联调计划

### 阶段1：修复代码不一致问题 ✅ 计划中

**任务清单：**
- [ ] 统一 `TemplateRecorder.extractAngleFeatures` 和 `CustomPoseActionDetector.extractAngleFeatures` 的 fallback 逻辑
- [ ] 移除未使用的 `VisionTemplateMatcher`（或确认是否需要保留）
- [ ] 在 `CustomPoseActionDetector` 中添加 feature vector 解码后的日志
- [ ] 在 `TemplateViewModel.stopRecording` 中增加 feature vector 大小日志

**验收标准：**
- 录制和计数使用完全相同的特征提取逻辑
- 编译通过无报错

---

### 阶段2：真机验证模板录制 🟡 待开始

**操作步骤：**
1. 打开模板录制页面
2. 录制一个简单动作（如深蹲，持续3秒）
3. 观察录制完成后模板是否出现在列表中
4. 通过 Android Studio 的 Database Inspector 或日志确认 featureVector 非空且大小合理

**验收标准：**
- 录制成功，模板出现在列表
- featureVector 非空（预期大小：~30-50 帧 × 9 维 × 4 字节 + 8 字节 header）
- 无崩溃或异常

---

### 阶段3：真机验证计数匹配 🟡 待开始

**操作步骤：**
1. 打开计数页面
2. 选择阶段2录制的模板
3. 做相同动作，观察：
   - 骨架是否正常显示
   - 计数是否触发
   - 日志中的 similarity/movementScore/periodicityScore 值

**验收标准：**
- 选择模板后计数页面正常显示骨架
- 做动作时能看到 debugInfo（如 similarity 值）
- 能正确计数（至少能触发一次）

---

### 阶段4：参数调优 🟡 待开始

**待调参数：**
- `CustomPoseActionDetector.matchThreshold`（默认 0.7f）
- `MIN_MOVEMENT_THRESHOLD`（默认 0.05f）
- `MIN_PERIODICITY_SCORE`（默认 0.15f）
- `customCooldownFrames`（默认 15）
- `TemplateRecorder` 中的 `MIN_MOTION`（默认 0.05f）

**调优方法：**
1. 观察日志中的 similarity 值分布
2. 根据实际匹配效果逐步调整阈值
3. 避免误触发（false positive）和漏触发（false negative）

**验收标准：**
- 正常做动作时能稳定触发计数
- 静止时不误触发
- 不同速度的动作都能有较好的匹配率

---

## 四、完成情况追踪

| 阶段 | 任务 | 状态 | 备注 |
|------|------|------|------|
| 阶段1 | 修复特征提取不一致 | ✅ 已完成 | 统一了脚踝/手腕 fallback 逻辑 |
| 阶段1 | 增强日志 | ✅ 已完成 | TemplateViewModel + CustomPoseActionDetector |
| 阶段1 | 移除 VisionTemplateMatcher | ⬜ 待确认 | 是否需要移除？ |
| 阶段2 | 真机验证模板录制 | ⬜ 未开始 | |
| 阶段3 | 真机验证计数匹配 | ⬜ 未开始 | |
| 阶段4 | 参数调优 | ⬜ 未开始 | |

---

## 五、关键日志 tag

联调期间关注以下日志 tag：
- `CustomPoseActionDetector` — 模板匹配结果、similarity、movementScore、periodicityScore
- `TemplateRecorder` — 录制进度、feature extraction
- `TFLite` — 推理性能、关键点位
- `CountingViewModel` — 模板加载、featureVector 大小
- `TemplateViewModel` — 录制状态、保存结果

---

## 六、附：编码格式说明

TemplateRecorder 的 featureVector 编码格式：
```
[4 bytes: frameCount] [4 bytes: featureDim] [frame1 data...] [frame2 data...] ...
```

每帧数据：`[float0][float1]...[float8]`（9 个 float，36 字节）

CustomPoseActionDetector.decodeFeatureSequence 解码时读取此格式。
