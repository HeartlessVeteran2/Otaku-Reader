package app.otakureader.server.service

import app.otakureader.server.config.AppConfig
import java.io.File
import java.io.IOException

/**
 * Service handling sync snapshot storage and retrieval.
 */
class SyncService(private val config: AppConfig) {
    
    private val snapshotFile = File(config.storagePath, "sync_snapshot.json")
    private val timestampFile = File(config.storagePath, "sync_timestamp.txt")
    
    /**
     * Store a sync snapshot.
     * @return The size of stored data in bytes (UTF-8 encoded)
     */
    fun storeSnapshot(data: String, timestamp: Long): Result<Int> = try {
        snapshotFile.writeText(data)
        timestampFile.writeText(timestamp.toString())
        Result.success(data.toByteArray(Charsets.UTF_8).size)
    } catch (e: IOException) {
        Result.failure(e)
    }

    /**
     * Retrieve the stored sync snapshot.
     * @return Pair of (data, timestamp) or null if no snapshot exists
     */
    fun getSnapshot(): Pair<String, Long>? {
        if (!snapshotFile.exists() || !timestampFile.exists()) {
            return null
        }

        return try {
            val data = snapshotFile.readText()
            val timestamp = timestampFile.readText().toLongOrNull() ?: System.currentTimeMillis()
            Pair(data, timestamp)
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Delete the stored snapshot.
     */
    fun deleteSnapshot(): Result<Unit> = try {
        val snapshotDeleted = if (snapshotFile.exists()) snapshotFile.delete() else true
        val timestampDeleted = if (timestampFile.exists()) timestampFile.delete() else true

        if (snapshotDeleted && timestampDeleted) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("Failed to delete snapshot files"))
        }
    } catch (e: SecurityException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    /**
     * Get the timestamp of the stored snapshot.
     */
    fun getTimestamp(): Long? {
        if (!timestampFile.exists()) return null
        return try {
            timestampFile.readText().toLongOrNull()
        } catch (e: IOException) {
            null
        }
    }
}
