package com.snuabar.counter.data.remote

import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV remote backup implementation - Skeleton.
 *
 * Full implementation requires:
 * 1. Add OkHttp dependency in build.gradle.kts:
 *    implementation("com.squareup.okhttp3:okhttp:4.12.0")
 *
 * 2. Implement actual HTTP/WebDAV methods (PROPFIND, PUT, GET, etc.)
 */
@Singleton
class WebDAVRemoteBackupDataSource @Inject constructor() : RemoteBackupDataSource {

    private var baseUrl: String = ""
    private var username: String = ""
    private var password: String = ""

    fun configure(baseUrl: String, username: String, password: String) {
        this.baseUrl = baseUrl
        this.username = username
        this.password = password
    }

    override suspend fun upload(data: String, remotePath: String): Boolean {
        // TODO: Implement WebDAV PUT request using OkHttp
        // val url = "$baseUrl/$remotePath"
        // val request = Request.Builder()
        //     .url(url)
        //     .put(RequestBody.create(MediaType.parse("application/json"), data))
        //     .build()
        // return client.newCall(request).execute().isSuccessful
        return false
    }

    override suspend fun download(remotePath: String): String? {
        // TODO: Implement WebDAV GET request using OkHttp
        // val url = "$baseUrl/$remotePath"
        // val request = Request.Builder().url(url).build()
        // val response = client.newCall(request).execute()
        // return if (response.isSuccessful) response.body?.string() else null
        return null
    }

    override suspend fun testConnection(): Boolean {
        // TODO: Implement connection test (e.g., PROPFIND on root)
        return false
    }
}
