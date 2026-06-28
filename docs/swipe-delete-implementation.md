# 闹钟卡片左滑删除功能实现文档

## 1. 概述

本项目中的闹钟卡片列表支持**左滑删除**交互。用户向左滑动卡片后，卡片会露出右侧的红色删除区域，点击删除图标即可删除该闹钟。

实现采用 **Jetpack Compose 纯自定义手势方案**，不依赖任何第三方库。

---

## 2. 涉及文件

| 文件 | 作用 |
|------|------|
| `AlarmCard.kt` | 左滑删除的核心 UI 组件，包含手势检测、动画和删除按钮 |
| `HomeScreen.kt` | 管理卡片展开状态（`expandedAlarmId`），协调多个卡片的互斥展开 |

---

## 3. 核心实现原理

### 3.1 双层布局结构

每个 `AlarmCard` 由**两层**组成：

```
┌─────────────────────────────┐
│  Box (外层容器)              │
│  ┌─────────────────────────┐│
│  │  背景层 (红色删除区域)   ││
│  │         🗑️ 删除图标       ││
│  └─────────────────────────┘│
│  ┌─────────────────────────┐│
│  │  前景层 (闹钟卡片内容)    ││
│  │  ☀️ 闹钟名称  [开关]     ││
│  └─────────────────────────┘│
└─────────────────────────────┘
```

- **背景层**：红色背景 + 右侧删除图标，始终位于底层
- **前景层**：实际的闹钟卡片，通过 `offset` 修饰符水平移动，露出背景层

### 3.2 关键代码（AlarmCard.kt）

```kotlin
@Composable
fun AlarmCard(
    alarm: Alarm,
    isExpanded: Boolean,          // 是否展开（由父组件控制）
    onExpandChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
    // ... 其他参数
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }  // 水平偏移量
    val maxSwipePx = with(LocalDensity.current) { 80.dp.toPx() }  // 最大滑动距离 = 80dp

    // ① 同步外部展开状态 → 自动动画
    LaunchedEffect(isExpanded) {
        val target = if (isExpanded) -maxSwipePx else 0f
        if (kotlin.math.abs(offsetX.value - target) > 1f) {
            offsetX.animateTo(target)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // ===== 背景层（红色删除区域）=====
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.error, shape = MaterialTheme.shapes.medium),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(
                onClick = {
                    onExpandChanged(false)
                    onDelete()
                },
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }

        // ===== 前景层（闹钟卡片）=====
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }  // 应用水平偏移
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        // ② 拖动结束时的阈值判断
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -maxSwipePx / 2) {  // 超过一半 → 展开
                                    offsetX.animateTo(-maxSwipePx)
                                    onExpandChanged(true)
                                } else {                                // 不足一半 → 收起
                                    offsetX.animateTo(0f)
                                    onExpandChanged(false)
                                }
                            }
                        },
                        // ③ 拖动过程中的实时偏移
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = (offsetX.value + dragAmount)
                                .coerceIn(-maxSwipePx, 0f)  // 限制在 [-80dp, 0] 范围内
                            scope.launch { offsetX.snapTo(newOffset) }
                        }
                    )
                }
        ) {
            // 卡片内容（闹钟名称、时间、开关等）
            // 点击卡片时：如果已展开则收起，否则打开编辑
        }
    }
}
```

### 3.3 手势检测流程

```
用户手指按下 → 开始水平拖动
       ↓
onHorizontalDrag 触发
       ↓
实时计算新偏移量（限制在 [-maxSwipePx, 0] 范围内）
       ↓
手指松开 → onDragEnd 触发
       ↓
判断最终偏移量是否超过阈值（-maxSwipePx / 2）
   ├─ 是 → 动画展开到 -maxSwipePx，回调 onExpandChanged(true)
   └─ 否 → 动画收回到 0f，回调 onExpandChanged(false)
```

### 3.4 阈值机制

| 参数 | 值 | 说明 |
|------|---|------|
| `maxSwipePx` | `80.dp` | 最大滑动距离（卡片向左移动的最大距离） |
| 展开阈值 | `-maxSwipePx / 2` | 滑动超过 40dp 时自动展开 |
| 展开状态偏移 | `-maxSwipePx` | 展开时卡片向左移动 80dp，露出删除区域 |
| 收起状态偏移 | `0f` | 卡片恢复原位 |

---

## 4. 状态管理（HomeScreen.kt）

### 4.1 互斥展开

列表中**只能有一个卡片**处于展开状态：

```kotlin
// HomeScreen 中定义
var expandedAlarmId by remember { mutableStateOf<String?>(null) }  // 当前展开卡片的 ID
```

当用户滑动卡片 A 时：
```kotlin
AlarmCard(
    isExpanded = expandedAlarmId == alarm.id,  // 仅当前卡片 ID 匹配时才展开
    onExpandChanged = { expanded ->
        expandedAlarmId = if (expanded) alarm.id else null
    }
)
```

### 4.2 自动收起机制

| 触发条件 | 实现代码 |
|---------|---------|
| **列表滚动时** | `if (listState.isScrollInProgress && expandedAlarmId != null) { expandedAlarmId = null }` |
| **点击 FAB（添加按钮）** | `onClick = { expandedAlarmId = null; showAddSheet = true }` |
| **点击设置按钮** | `onClick = { expandedAlarmId = null; showSettings = true }` |
| **点击位置选择** | `onLocationClick = { expandedAlarmId = null; showLocationPicker = true }` |

### 4.3 删除与撤销

```kotlin
onDelete = {
    // ① 保存被删除的闹钟（用于撤销）
    recentlyDeletedAlarm = alarm

    coroutineScope.launch {
        // ② 取消该闹钟的定时任务
        alarmManagerHelper.cancelAlarm(alarm)

        // ③ 从数据库删除
        alarmRepository.deleteAlarm(alarm)

        // ④ 显示 Snackbar 提示 + 撤销操作
        val result = snackbarHostState.showSnackbar(
            message = "闹钟已删除",
            actionLabel = "撤销",
            duration = SnackbarDuration.Short
        )

        if (result == SnackbarResult.ActionPerformed) {
            // ⑤ 用户点击撤销 → 恢复闹钟
            recentlyDeletedAlarm?.let { deletedAlarm ->
                alarmRepository.insertAlarm(deletedAlarm)
                if (deletedAlarm.isEnabled) {
                    alarmManagerHelper.scheduleAlarm(deletedAlarm, currentLat, currentLng)
                }
            }
        }
        recentlyDeletedAlarm = null
    }
}
```

---

## 5. 交互细节

### 5.1 点击卡片时的行为

```kotlin
.clickable {
    if (isExpanded) {
        // 已展开 → 收起
        onExpandChanged(false)
    } else {
        // 未展开 → 打开编辑弹窗
        onClick()
    }
}
```

### 5.2 开关切换时的行为

```kotlin
Switch(
    onCheckedChange = { enabled ->
        if (isExpanded) {
            onExpandChanged(false)  // 先收起
        }
        onToggle(enabled)  // 再切换开关状态
    }
)
```

---

## 6. 技术要点总结

| 技术点 | 实现方式 |
|-------|---------|
| **手势检测** | `pointerInput` + `detectHorizontalDragGestures` |
| **动画系统** | `Animatable<Float>` + `animateTo()` / `snapTo()` |
| **滑动限制** | `coerceIn(-maxSwipePx, 0f)` |
| **双层布局** | `Box` 嵌套：背景层（`matchParentSize`）+ 前景层（`offset`） |
| **状态同步** | `LaunchedEffect(isExpanded)` 响应外部状态变化 |
| **互斥展开** | 父组件维护 `expandedAlarmId`，仅匹配 ID 的卡片展开 |
| **自动收起** | 列表滚动、点击其他按钮时重置 `expandedAlarmId = null` |
| **删除撤销** | Snackbar + `recentlyDeletedAlarm` 缓存 + 数据库回滚 |

---

## 7. 可复用模式

如需在其他列表中复用此模式，只需：

1. **复制 `AlarmCard` 的结构**：双层 `Box` + `Animatable` 偏移 + `detectHorizontalDragGestures`
2. **在父组件中维护展开状态**：类似 `expandedItemId` 的变量
3. **处理互斥展开**：设置新展开项时，自动收起其他项
4. **添加自动收起**：滚动、切换页面、点击操作时重置状态
