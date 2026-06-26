package com.snuabar.counter.core.detection.tflite

/**
 * Pose detection model configuration.
 * Maps to MediaPipe PoseLandmarker .task model files.
 */
enum class PoseModelConfig(
    val displayName: String,
    val fileName: String,
    val description: String
) {
    /** Fast mode: PoseLandmarker Lite - smallest & fastest, for simple actions */
    FAST(
        displayName = "快速",
        fileName = "blazepose/pose_landmarker_lite.task",
        description = "速度优先，适合跳绳、拍手等简单动作"
    ),

    /** Standard mode: PoseLandmarker Full - balanced speed and accuracy */
    STANDARD(
        displayName = "标准",
        fileName = "blazepose/pose_landmarker_full.task",
        description = "平衡速度与精度"
    ),

    /** Precise mode: PoseLandmarker Heavy - highest accuracy for complex actions */
    PRECISE(
        displayName = "精确",
        fileName = "blazepose/pose_landmarker_heavy.task",
        description = "精度优先，适合俯卧撑、深蹲等复杂动作"
    )
}
