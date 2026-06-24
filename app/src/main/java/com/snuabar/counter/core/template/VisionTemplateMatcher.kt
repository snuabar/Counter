package com.snuabar.counter.core.template

import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.Template
import kotlin.math.sqrt

class VisionTemplateMatcher : TemplateMatcher {

    override fun supports(sensorType: SensorType): Boolean {
        return sensorType == SensorType.VISION
    }

    override fun match(input: FloatArray, template: Template): MatchResult {
        val featureVector = template.featureVector
            ?: return MatchResult(score = 0f, isMatch = false)

        val templateFeatures = featureVector.toFloatArray()
        if (input.size != templateFeatures.size) {
            return MatchResult(score = 0f, isMatch = false)
        }

        // Cosine similarity
        val dotProduct = input.zip(templateFeatures).sumOf { (a, b) -> (a * b).toDouble() }
        val inputNorm = sqrt(input.sumOf { (it * it).toDouble() })
        val templateNorm = sqrt(templateFeatures.sumOf { (it * it).toDouble() })

        val similarity = if (inputNorm > 0 && templateNorm > 0) {
            (dotProduct / (inputNorm * templateNorm)).toFloat()
        } else {
            0f
        }

        return MatchResult(
            score = similarity,
            isMatch = similarity >= template.threshold
        )
    }

    // Extension function to convert ByteArray to FloatArray
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
