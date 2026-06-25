package com.snuabar.counter.core.detection.tflite.action

import com.snuabar.counter.domain.model.ActionType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Abstract base class for pose action detectors.
 * Provides common utilities like angle calculation and state management.
 */
abstract class BasePoseActionDetector(
    protected val actionType: ActionType
) : PoseActionDetector {

    protected var currentState: ActionState = ActionState.IDLE
    protected var count: Int = 0
    protected var confidence: Float = 0f
    
    // Cooldown to prevent double-counting
    protected val cooldownFrames: Int = 10
    protected var cooldownCounter: Int = 0

    override fun reset() {
        currentState = ActionState.IDLE
        count = 0
        confidence = 0f
        cooldownCounter = 0
    }

    /**
     * Calculate angle (in degrees) between three points: center -> point1 and center -> point2.
     * @return angle in degrees (0-180)
     */
    protected fun calculateAngle(
        p1x: Float, p1y: Float,
        cx: Float, cy: Float,
        p2x: Float, p2y: Float
    ): Float {
        val angle1 = atan2(p1y - cy, p1x - cx)
        val angle2 = atan2(p2y - cy, p2x - cx)
        var angle = abs(angle1 - angle2) * 180f / PI.toFloat()
        if (angle > 180f) angle = 360f - angle
        return angle
    }

    /**
     * Calculate distance between two points.
     */
    protected fun calculateDistance(
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Float {
        return sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    }

    /**
     * Check if point is visible (confidence > threshold).
     */
    protected fun isVisible(keypoint: FloatArray, threshold: Float = 0.3f): Boolean {
        return keypoint[2] > threshold
    }

    protected fun updateCooldown() {
        if (cooldownCounter > 0) cooldownCounter--
    }

    protected fun startCooldown() {
        cooldownCounter = cooldownFrames
    }

    protected fun isInCooldown(): Boolean {
        return cooldownCounter > 0
    }
}
