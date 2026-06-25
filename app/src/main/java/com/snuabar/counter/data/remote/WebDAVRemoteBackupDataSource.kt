package com.snuabar.counter.data.remote

import android.util.Base64
import com.snuabar.counter.data.local.prefs.BackupPreferences
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV remote backup implementation using OkHttp.
 *
 * Supports Basic Auth authentication and standard WebDAV methods
 * (PROPFIND for connection test, PUT for upload, GET for download).
 */
@Singleton
class WebDAVRemoteBackupDataSource @Inject constructor(
    private val client: OkHttpClient,
    private val backupPreferences: BackupPreferences
) : RemoteBackupDataSource {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun getBaseUrl(): String = backupPreferences.baseUrlFlow.first().trimEnd('/')
    private suspend fun getUsername(): String = backupPreferences.usernameFlow.first()
    private suspend fun getPassword(): String = backupPreferences.passwordFlow.first()

    private suspend fun buildAuthHeader(): String {
        val username = getUsername()
        val password = getPassword()
        val credentials = "$username:$password"
        return "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildUrl(baseUrl: String, remotePath: String): String {
        val path = remotePath.trimStart('/')
        return "$baseUrl/$path"
    }

    override suspend fun upload(data: String, remotePath: String): Boolean {
        return try {
            val baseUrl = getBaseUrl()
            val url = buildUrl(baseUrl, remotePath)
            val requestBody = data.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .header("Authorization", buildAuthHeader())
                .header("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun download(remotePath: String): String? {
        return try {
            val baseUrl = getBaseUrl()
            val url = buildUrl(baseUrl, remotePath)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", buildAuthHeader())
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun testConnection(): Boolean {
        return try {
            val baseUrl = getBaseUrl()
            val request = Request.Builder()
                .url(baseUrl)
                .method("PROPFIND", null)
                .header("Authorization", buildAuthHeader())
                .header("Depth", "0")
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
