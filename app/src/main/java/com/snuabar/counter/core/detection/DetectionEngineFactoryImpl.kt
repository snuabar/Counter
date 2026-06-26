package com.snuabar.counter.core.detection

import android.content.Context
import com.snuabar.counter.core.detection.audio.AudioDetectionEngine
import com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine
import com.snuabar.counter.core.template.RecordingSession
import com.snuabar.counter.data.local.prefs.DetectionPreferences
import com.snuabar.counter.domain.model.SensorType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionEngineFactoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingSession: RecordingSession,
    private val detectionPreferences: DetectionPreferences
) : DetectionEngineFactory {

    override fun create(sensorType: SensorType, engineType: EngineType): DetectionEngine {
        return when (sensorType) {
            SensorType.VISION -> TFLiteDetectionEngine(context, recordingSession, detectionPreferences)
            SensorType.AUDIO -> AudioDetectionEngine(context)
        }
    }
}
