package com.snuabar.counter.core.detection.vision

class MotionDetector(private val differencer: FrameDifferencer = FrameDifferencer()) {
    private var count = 0
    private var lastMotionTime = 0L
    private val cooldownMs = 500 // Minimum time between counts

    fun detect(diff: Double, threshold: Double): Boolean {
        val currentTime = System.currentTimeMillis()
        if (diff > threshold && (currentTime - lastMotionTime) > cooldownMs) {
            count++
            lastMotionTime = currentTime
            return true
        }
        return false
    }

    fun getCount(): Int = count

    fun reset() {
        count = 0
        lastMotionTime = 0L
        differencer.reset()
    }
}
