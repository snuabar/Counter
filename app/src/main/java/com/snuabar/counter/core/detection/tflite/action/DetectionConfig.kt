package com.snuabar.counter.core.detection.tflite.action

/**
 * 自定义动作检测配置，每种优化算法可独立开关。
 * 默认全部开启（除了在线学习），方便向后兼容。
 */
data class DetectionConfig(
    /** B: 特征维度自适应加权 —— 基于模板各维度方差动态调整 DTW 距离权重 */
    val useAdaptiveWeights: Boolean = true,

    /** A: 速度/加速度特征 —— 需要模板也包含速度特征 */
    val useVelocityFeatures: Boolean = true,

    /** E: 自适应平滑 —— 根据模板动作速度动态调整平滑窗口 */
    val useAdaptiveSmoothing: Boolean = true,

    /** F: 模板在线学习 —— 默认关闭，需要持续运行才能积累效果 */
    val useOnlineLearning: Boolean = false,

    /** D: 身高/距离归一化 —— 谨慎开启，配合站位框使用 */
    val useNormalization: Boolean = false
)
