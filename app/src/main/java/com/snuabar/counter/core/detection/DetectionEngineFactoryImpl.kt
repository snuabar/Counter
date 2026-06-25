package com.snuabar.counter.core.detection

import android.content.Context
import com.snuabar.counter.core.detection.audio.AudioDetectionEngine
import com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine
import com.snuabar.counter.core.detection.vision.VisionDetectionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionEngineFactoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionEngineFactory {

    override fun create(sensorType: SensorType, engineType: EngineType): DetectionEngine {
        return when (sensorType) {
            SensorType.VISION -> when (engineType) {
                EngineType.OPEN_CV -> VisionDetectionEngine(context)
                EngineType.TFLITE -> TFLiteDetectionEngine(context)
            }
            SensorType.AUDIO -> AudioDetectionEngine(context)
        }
    }
}
