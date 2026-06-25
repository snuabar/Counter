package com.snuabar.counter.core.detection.vision

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class FrameDifferencer {
    private var previousFrame: Mat? = null

    fun computeDifference(currentFrame: Mat): Double {
        val gray = if (currentFrame.channels() == 1) {
            currentFrame.clone()
        } else {
            val g = Mat()
            Imgproc.cvtColor(currentFrame, g, Imgproc.COLOR_RGB2GRAY)
            g
        }

        val diff = if (previousFrame != null) {
            val result = Mat()
            Core.absdiff(previousFrame, gray, result)
            val mean = Core.mean(result)
            result.release()
            mean.`val`[0]
        } else {
            0.0
        }

        previousFrame?.release()
        previousFrame = gray.clone()
        gray.release()

        return diff
    }

    fun reset() {
        previousFrame?.release()
        previousFrame = null
    }
}
