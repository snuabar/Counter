package com.snuabar.counter.data.remote

/**
 * Remote backup data source interface.
 * Implementations can support WebDAV, cloud storage, or custom APIs.
 */
interface RemoteBackupDataSource {
    /**
     * Upload backup data to remote server.
     * @param data JSON backup string
     * @param remotePath destination path on remote server
     * @return true if upload succeeded
     */
    suspend fun upload(data: String, remotePath: String): Boolean

    /**
     * Download backup data from remote server.
     * @param remotePath source path on remote server
     * @return JSON backup string, or null if download failed
     */
    suspend fun download(remotePath: String): String?

    /**
     * Test connection to remote server.
     * @return true if connection is valid
     */
    suspend fun testConnection(): Boolean
}
