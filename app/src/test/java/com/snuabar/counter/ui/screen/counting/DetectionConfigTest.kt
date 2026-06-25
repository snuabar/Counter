package com.snuabar.counter.ui.screen.counting

import com.snuabar.counter.core.detection.DetectionConfig
import com.snuabar.counter.core.detection.DetectionEngine
import com.snuabar.counter.core.detection.SensorType
import com.snuabar.counter.domain.model.SessionMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@ExperimentalCoroutinesApi
class DetectionConfigTest {

    @Test
    fun `default DetectionConfig should have correct defaults`() = runTest {
        val config = DetectionConfig(
            sensorType = SensorType.VISION
        )

        assertEquals(SensorType.VISION, config.sensorType)
        assertEquals(0.7f, config.threshold, 0.01f)
        assertEquals(SessionMode.COUNTING, config.mode)
        assertNull(config.targetSeconds)
        assertEquals(640, config.targetResolution.width)
        assertEquals(480, config.targetResolution.height)
    }

    @Test
    fun `timer mode DetectionConfig should have targetSeconds`() = runTest {
        val config = DetectionConfig(
            sensorType = SensorType.VISION,
            mode = SessionMode.TIMER,
            targetSeconds = 60
        )

        assertEquals(SessionMode.TIMER, config.mode)
        assertEquals(60, config.targetSeconds)
    }

    @Test
    fun `DetectionConfig should support custom resolution`() = runTest {
        val config = DetectionConfig(
            sensorType = SensorType.VISION,
            targetResolution = android.util.Size(320, 240)
        )

        assertEquals(320, config.targetResolution.width)
        assertEquals(240, config.targetResolution.height)
    }
}
