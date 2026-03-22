package app.otakureader.feature.reader.panel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.otakureader.feature.reader.model.PageAnalysisResult
import app.otakureader.feature.reader.model.PanelAnalysisException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for caching panel analysis results.
 * 
 * This service provides persistent caching of Gemini Vision panel analysis
 * results to avoid re-analyzing the same pages. It uses a hybrid approach:
 * - DataStore for metadata and quick lookups
 * - Files for storing full analysis results (JSON)
 * 
 * **Key features:**
 * - Image hash-based lookup
 * - Configurable cache size limits
 * - LRU (Least Recently Used) eviction
 * - Automatic stale data cleanup
 * 
 * **Cache structure:**
 * ```
 * /data/data/app.otakureader/cache/panel_analysis/
 * ├── metadata/          # DataStore for metadata
 * └── results/           # JSON files with analysis data
 *     ├── abcd1234.json
 *     └── efgh5678.json
 * ```
 */
@Singleton
class PanelCacheService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
    private val resultsDir = File(cacheDir, "results")
    
    private val metadataDataStore: DataStore<Preferences> = context.panelCacheDataStore

    init {
        // Ensure cache directories exist
        if (!resultsDir.exists()) {
            resultsDir.mkdirs()
        }
    }

    /**
     * Get cached analysis result for an image hash.
     *
     * @param imageHash The SHA-256 hash of the image
     * @return The cached PageAnalysisResult, or null if not found or stale
     */
    suspend fun getCachedResult(imageHash: String): PageAnalysisResult? = withContext(Dispatchers.IO) {
        try {
            requireSafeHash(imageHash)
            // Check if we have metadata for this hash
            val metadata = getMetadata(imageHash)
            if (metadata == null) {
                return@withContext null
            }

            // Check if result is stale
            if (metadata.isStale()) {
                deleteCachedResult(imageHash)
                return@withContext null
            }

            // Load the result file
            val resultFile = File(resultsDir, "${imageHash}.json")
            if (!resultFile.exists()) {
                deleteMetadata(imageHash)
                return@withContext null
            }

            // Parse the result
            val json = resultFile.readText()
            val result = PageAnalysisResult.fromJson(json)

            // Update access time (LRU tracking)
            if (result != null) {
                updateAccessTime(imageHash)
            }

            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cache an analysis result.
     *
     * @param imageHash The SHA-256 hash of the image
     * @param result The analysis result to cache
     * @return true if caching succeeded, false otherwise
     */
    suspend fun cacheResult(
        imageHash: String,
        result: PageAnalysisResult
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            requireSafeHash(imageHash)
            // Check cache size and evict if needed
            ensureCacheSpace()

            // Save result to file
            val resultFile = File(resultsDir, "${imageHash}.json")
            resultFile.writeText(result.toJson())

            // Save metadata
            val metadata = CacheMetadata(
                imageHash = imageHash,
                timestamp = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis(),
                panelCount = result.panels.size,
                fileSize = resultFile.length()
            )
            saveMetadata(metadata)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete a cached result.
     *
     * @param imageHash The hash of the result to delete
     */
    suspend fun deleteCachedResult(imageHash: String) = withContext(Dispatchers.IO) {
        try {
            requireSafeHash(imageHash)
            // Delete result file
            val resultFile = File(resultsDir, "${imageHash}.json")
            if (resultFile.exists()) {
                resultFile.delete()
            }

            // Delete metadata
            deleteMetadata(imageHash)
        } catch (e: Exception) {
            // Ignore errors during deletion
        }
    }

    /**
     * Clear all cached results.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            // Delete all result files
            resultsDir.listFiles()?.forEach { it.delete() }

            // Clear metadata
            metadataDataStore.edit { preferences ->
                preferences.clear()
            }
        } catch (e: Exception) {
            throw PanelAnalysisException.CacheError("Failed to clear cache", e)
        }
    }

    /**
     * Get cache statistics.
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        try {
            val allMetadata = getAllMetadata()
            val totalSize = allMetadata.sumOf { it.fileSize }
            val resultCount = allMetadata.size

            CacheStats(
                entryCount = resultCount,
                totalSizeBytes = totalSize,
                maxSizeBytes = MAX_CACHE_SIZE_BYTES,
                oldestEntryTimestamp = allMetadata.minByOrNull { it.timestamp }?.timestamp
            )
        } catch (e: Exception) {
            CacheStats(0, 0, MAX_CACHE_SIZE_BYTES, null)
        }
    }

    /**
     * Clean up stale entries from cache.
     */
    suspend fun cleanupStaleEntries(maxAgeDays: Int = DEFAULT_MAX_AGE_DAYS) = withContext(Dispatchers.IO) {
        try {
            val allMetadata = getAllMetadata()
            val now = System.currentTimeMillis()
            val maxAgeMillis = maxAgeDays.toLong() * 24 * 60 * 60 * 1000

            allMetadata.forEach { metadata ->
                if (now - metadata.timestamp > maxAgeMillis) {
                    deleteCachedResult(metadata.imageHash)
                }
            }
        } catch (e: Exception) {
            // Ignore errors during cleanup
        }
    }

    /**
     * Ensure there's enough space in the cache.
     * Evicts oldest entries if needed.
     */
    private suspend fun ensureCacheSpace() {
        val stats = getCacheStats()
        
        if (stats.totalSizeBytes < MAX_CACHE_SIZE_BYTES * 0.8) {
            // Plenty of space
            return
        }

        // Need to evict some entries
        val allMetadata = getAllMetadata()
        val sortedByAccess = allMetadata.sortedBy { it.lastAccessed }

        var currentSize = stats.totalSizeBytes
        for (metadata in sortedByAccess) {
            if (currentSize < MAX_CACHE_SIZE_BYTES * 0.6) {
                // Reduced to 60% capacity, stop evicting
                break
            }

            deleteCachedResult(metadata.imageHash)
            currentSize -= metadata.fileSize
        }
    }

    /**
     * Get metadata for a specific hash.
     */
    private suspend fun getMetadata(imageHash: String): CacheMetadata? {
        val key = stringPreferencesKey("metadata_$imageHash")
        val json = metadataDataStore.data.map { preferences ->
            preferences[key]
        }.first()

        return json?.let { CacheMetadata.fromJson(it) }
    }

    /**
     * Save metadata.
     */
    private suspend fun saveMetadata(metadata: CacheMetadata) {
        val key = stringPreferencesKey("metadata_${metadata.imageHash}")
        metadataDataStore.edit { preferences ->
            preferences[key] = metadata.toJson()
        }
    }

    /**
     * Delete metadata for a hash.
     */
    private suspend fun deleteMetadata(imageHash: String) {
        val key = stringPreferencesKey("metadata_$imageHash")
        metadataDataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    /**
     * Get all metadata entries.
     */
    private suspend fun getAllMetadata(): List<CacheMetadata> {
        val preferences = metadataDataStore.data.first()
        
        return preferences.asMap().values
            .filterIsInstance<String>()
            .mapNotNull { CacheMetadata.fromJson(it) }
    }

    /**
     * Update the last accessed time for an entry.
     */
    private suspend fun updateAccessTime(imageHash: String) {
        val metadata = getMetadata(imageHash) ?: return
        saveMetadata(metadata.copy(lastAccessed = System.currentTimeMillis()))
    }

    companion object {
        private const val CACHE_DIR_NAME = "panel_analysis"
        private const val DEFAULT_MAX_AGE_DAYS = 30
        private const val MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024L  // 50 MB

        /**
         * Validates that [imageHash] contains only hex characters (0-9, a-f, A-F) and
         * hyphens, which is the expected format for a SHA-256 hash. Rejects any value
         * containing path separators or "..", preventing path-traversal attacks where a
         * crafted hash like "../../../../sensitive/file" could read or overwrite arbitrary
         * files on the device.
         *
         * @throws IllegalArgumentException if the hash contains unsafe characters.
         */
        internal fun requireSafeHash(imageHash: String) {
            require(imageHash.isNotBlank()) { "imageHash must not be blank" }
            require(imageHash.matches(Regex("[0-9a-fA-F\\-]+"))) {
                "imageHash contains unsafe characters: $imageHash"
            }
        }

        private val Context.panelCacheDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "panel_cache_metadata"
        )
    }
}

/**
 * Metadata for a cached analysis result.
 */
private data class CacheMetadata(
    val imageHash: String,
    val timestamp: Long,
    val lastAccessed: Long,
    val panelCount: Int,
    val fileSize: Long
) {
    fun isStale(maxAgeDays: Int = 30): Boolean {
        val maxAgeMillis = maxAgeDays.toLong() * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - timestamp > maxAgeMillis
    }

    fun toJson(): String = "{\"hash\":\"$imageHash\",\"time\":$timestamp,\"access\":$lastAccessed,\"count\":$panelCount,\"size\":$fileSize}"

    companion object {
        fun fromJson(json: String): CacheMetadata? = try {
            // Simple JSON parsing
            val hash = extractValue(json, "hash") ?: return null
            val time = extractLong(json, "time") ?: return null
            val access = extractLong(json, "access") ?: time
            val count = extractInt(json, "count") ?: 0
            val size = extractLong(json, "size") ?: 0

            CacheMetadata(hash, time, access, count.toInt(), size)
        } catch (e: Exception) {
            null
        }

        private fun extractValue(json: String, key: String): String? {
            val match = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)
            return match?.groupValues?.get(1)
        }

        private fun extractLong(json: String, key: String): Long? {
            val match = Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)
            return match?.groupValues?.get(1)?.toLongOrNull()
        }

        private fun extractInt(json: String, key: String): Int? {
            val match = Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)
            return match?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}

/**
 * Cache statistics.
 */
data class CacheStats(
    val entryCount: Int,
    val totalSizeBytes: Long,
    val maxSizeBytes: Long,
    val oldestEntryTimestamp: Long?
) {
    val usagePercent: Float
        get() = (totalSizeBytes.toFloat() / maxSizeBytes * 100).coerceIn(0f, 100f)

    val formattedSize: String
        get() = formatBytes(totalSizeBytes)

    val formattedMaxSize: String
        get() = formatBytes(maxSizeBytes)

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }
}
