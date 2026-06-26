package com.snuabar.counter.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore by preferencesDataStore(name = "backup_preferences")

@Singleton
class BackupPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.backupDataStore

    companion object {
        val WEBDAV_BASE_URL = stringPreferencesKey("webdav_base_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
    }

    val baseUrlFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[WEBDAV_BASE_URL] ?: "" }

    val usernameFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[WEBDAV_USERNAME] ?: "" }

    val passwordFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[WEBDAV_PASSWORD] ?: "" }

    suspend fun setWebDavConfig(baseUrl: String, username: String, password: String) {
        dataStore.edit { preferences ->
            preferences[WEBDAV_BASE_URL] = baseUrl
            preferences[WEBDAV_USERNAME] = username
            preferences[WEBDAV_PASSWORD] = password
        }
    }
}
