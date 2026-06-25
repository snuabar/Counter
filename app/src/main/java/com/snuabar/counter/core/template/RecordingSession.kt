package com.snuabar.counter.core.template

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared singleton that bridges the detection engine and the template recording system.
 * During recording, TFLiteDetectionEngine forwards keypoints here,
 * and they are routed to the active TemplateRecorder via the registered callback.
 */
@Singleton
class RecordingSession @Inject constructor() {

    private var keypointCallback: ((Array<FloatArray>) -> Unit)? = null

    fun setKeypointCallback(callback: ((Array<FloatArray>) -> Unit)?) {
        keypointCallback = callback
    }

    fun onKeypointsDetected(keypoints: Array<FloatArray>) {
        keypointCallback?.invoke(keypoints)
    }

    fun clearCallback() {
        keypointCallback = null
    }

    fun isRecording(): Boolean = keypointCallback != null
}
