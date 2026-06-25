package com.snuabar.counter.core.detection.tflite.action

/**
 * Interface for pose-based action detection.
 * Each implementation detects a specific exercise using keypoint geometry.
 */
interface PoseActionDetector {
    /** Detect action from keypoints. Returns null if no action detected. */
    fun detect(keypoints: Array<FloatArray>): PoseActionResult?
    
    /** Reset detector state (e.g., when session starts/ends). */
    fun reset()
}
