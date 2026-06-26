package com.snuabar.counter.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.snuabar.counter.core.detection.tflite.PoseModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.detectionDataStore by preferencesDataStore(name = "detection_preferences")

@Singleton
class DetectionPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.detectionDataStore

    companion object {
        val THRESHOLD = floatPreferencesKey("threshold")
        val POSE_MODEL_CONFIG = stringPreferencesKey("pose_model_config")
        val VOICE_ANNOUNCEMENT = booleanPreferencesKey("voice_announcement")
    }

    val thresholdFlow: Flow<Float> = dataStore.data
        .map { preferences ->
            preferences[THRESHOLD] ?: 0.7f
        }

    suspend fun setThreshold(value: Float) {
        dataStore.edit { preferences ->
            preferences[THRESHOLD] = value.coerceIn(0.1f, 1.0f)
        }
    }

    val poseModelConfigFlow: Flow<PoseModelConfig> = dataStore.data
        .map { preferences ->
            val configName = preferences[POSE_MODEL_CONFIG] ?: PoseModelConfig.STANDARD.name
            try {
                PoseModelConfig.valueOf(configName)
            } catch (e: IllegalArgumentException) {
                // Fallback for old cached values (e.g. BLAZEPOSE which was removed)
                PoseModelConfig.STANDARD
            }
        }

    suspend fun setPoseModelConfig(config: PoseModelConfig) {
        dataStore.edit { preferences ->
            preferences[POSE_MODEL_CONFIG] = config.name
        }
    }

    val voiceAnnouncementFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[VOICE_ANNOUNCEMENT] ?: false
        }

    suspend fun setVoiceAnnouncement(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[VOICE_ANNOUNCEMENT] = enabled
        }
    }
}
