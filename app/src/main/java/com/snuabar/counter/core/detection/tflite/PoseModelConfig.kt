package com.snuabar.counter.core.detection.tflite

/**
 * Pose detection model configuration.
 * Maps to TFLite model files in assets/single_pose_detection/
 */
enum class PoseModelConfig(
    val displayName: String,
    val fileName: String,
    val inputSize: Int,
    val description: String
) {
    /** Fast mode: Lightning Int8 - smallest & fastest, for simple actions */
    FAST(
        displayName = "快速",
        fileName = "single_pose_detection/lightning-int8.tflite",
        inputSize = 192,
        description = "速度优先，适合跳绳、拍手等简单动作"
    ),

    /** Standard mode: Lightning Float16 - balanced speed and accuracy */
    STANDARD(
        displayName = "标准",
        fileName = "single_pose_detection/lightning-fp16.tflite",
        inputSize = 192,
        description = "平衡速度与精度"
    ),

    /** Precise mode: Thunder Float16 - highest accuracy for complex actions */
    PRECISE(
        displayName = "精确",
        fileName = "single_pose_detection/thunder-fp16.tflite",
        inputSize = 256,
        description = "精度优先，适合俯卧撑、深蹲等复杂动作"
    )
}
