package com.snuabar.counter.core.template

import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.Template

interface TemplateMatcher {
    fun match(input: FloatArray, template: Template): MatchResult
    fun supports(sensorType: SensorType): Boolean
}

data class MatchResult(
    val score: Float,
    val isMatch: Boolean
)
