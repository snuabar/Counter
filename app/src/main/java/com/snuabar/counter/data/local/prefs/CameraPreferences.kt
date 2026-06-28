package com.snuabar.counter.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cameraDataStore by preferencesDataStore(name = "camera_preferences")

@Singleton
class CameraPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.cameraDataStore

    companion object {
        val COUNTING_CAMERA_ID = stringPreferencesKey("counting_camera_id")
        val COUNTING_IS_FRONT = booleanPreferencesKey("counting_is_front")
        val TEMPLATE_CAMERA_ID = stringPreferencesKey("template_camera_id")
        val TEMPLATE_IS_FRONT = booleanPreferencesKey("template_is_front")
    }

    // Counting screen camera
    val countingCameraId: Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[COUNTING_CAMERA_ID]
        }

    val countingIsFront: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[COUNTING_IS_FRONT] ?: false
        }

    suspend fun setCountingCamera(cameraId: String, isFront: Boolean) {
        dataStore.edit { preferences ->
            preferences[COUNTING_CAMERA_ID] = cameraId
            preferences[COUNTING_IS_FRONT] = isFront
        }
    }

    // Template screen camera
    val templateCameraId: Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[TEMPLATE_CAMERA_ID]
        }

    val templateIsFront: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[TEMPLATE_IS_FRONT] ?: false
        }

    suspend fun setTemplateCamera(cameraId: String, isFront: Boolean) {
        dataStore.edit { preferences ->
            preferences[TEMPLATE_CAMERA_ID] = cameraId
            preferences[TEMPLATE_IS_FRONT] = isFront
        }
    }
}
