package com.snuabar.counter.core.template

import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.Template
import kotlin.math.abs
import kotlin.math.min

class AudioTemplateMatcher : TemplateMatcher {

    override fun supports(sensorType: SensorType): Boolean {
        return sensorType == SensorType.AUDIO
    }

    override fun match(input: FloatArray, template: Template): MatchResult {
        val featureVector = template.featureVector
            ?: return MatchResult(score = 0f, isMatch = false)

        val templateFeatures = featureVector.toFloatArray()
        if (input.isEmpty() || templateFeatures.isEmpty()) {
            return MatchResult(score = 0f, isMatch = false)
        }

        // Dynamic Time Warping (DTW) distance
        val dtwDistance = computeDTW(input, templateFeatures)
        val maxDistance = input.size.toFloat() + templateFeatures.size.toFloat()
        val similarity = 1f - (dtwDistance / maxDistance).coerceIn(0f, 1f)

        return MatchResult(
            score = similarity,
            isMatch = similarity >= template.threshold
        )
    }

    private fun computeDTW(a: FloatArray, b: FloatArray): Float {
        val n = a.size
        val m = b.size
        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0f

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = abs(a[i - 1] - b[j - 1])
                dtw[i][j] = cost + minOf(
                    dtw[i - 1][j],
                    dtw[i][j - 1],
                    dtw[i - 1][j - 1]
                )
            }
        }

        return dtw[n][m]
    }

    private fun minOf(a: Float, b: Float, c: Float): Float {
        return min(min(a, b), c)
    }

    private fun ByteArray.toFloatArray(): FloatArray {
        val floatArray = FloatArray(this.size / 4)
        for (i in floatArray.indices) {
            val bytes = this.copyOfRange(i * 4, (i + 1) * 4)
            floatArray[i] = bytes.toFloat()
        }
        return floatArray
    }

    private fun ByteArray.toFloat(): Float {
        val intBits = (this[0].toInt() and 0xFF) or
                ((this[1].toInt() and 0xFF) shl 8) or
                ((this[2].toInt() and 0xFF) shl 16) or
                ((this[3].toInt() and 0xFF) shl 24)
        return Float.fromBits(intBits)
    }
}
