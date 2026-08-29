package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Preference store for download settings including auto-download configuration
 * and delete-after-reading behaviour.
 * Exposes reactive [Flow] properties and suspend setter functions backed by DataStore.
 */
class DownloadPreferences(private val dataStore: DataStore<Preferences>) {

    // --- Auto-Download ---

    /** Whether to automatically download new chapters when library update finds them. Default: false. */
    val autoDownloadEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_DOWNLOAD_ENABLED] ?: false }
    suspend fun setAutoDownloadEnabled(value: Boolean) = dataStore.edit { it[Keys.AUTO_DOWNLOAD_ENABLED] = value }

    /** Whether to download only when connected to Wi-Fi. Default: true. */
    val downloadOnlyOnWifi: Flow<Boolean> = dataStore.data.map { it[Keys.DOWNLOAD_ONLY_ON_WIFI] ?: true }
    suspend fun setDownloadOnlyOnWifi(value: Boolean) = dataStore.edit { it[Keys.DOWNLOAD_ONLY_ON_WIFI] = value }

    /** Maximum number of new chapters to auto-download per manga. Default: 3. */
    val autoDownloadLimit: Flow<Int> = dataStore.data.map { it[Keys.AUTO_DOWNLOAD_LIMIT] ?: 3 }
    suspend fun setAutoDownloadLimit(value: Int) = dataStore.edit { it[Keys.AUTO_DOWNLOAD_LIMIT] = value }

    // --- Download Location ---

    /** Custom download directory URI (null = default app storage) */
    val downloadLocation: Flow<String?> = dataStore.data.map { it[Keys.DOWNLOAD_LOCATION] }
    suspend fun setDownloadLocation(value: String?) = dataStore.edit {
        if (value != null) it[Keys.DOWNLOAD_LOCATION] = value else it.remove(Keys.DOWNLOAD_LOCATION)
    }

    // --- Concurrent Downloads ---

    /** Maximum concurrent downloads (1-5). Default: 2. */
    val concurrentDownloads: Flow<Int> = dataStore.data.map { it[Keys.CONCURRENT_DOWNLOADS] ?: 2 }
    suspend fun setConcurrentDownloads(value: Int) = dataStore.edit { it[Keys.CONCURRENT_DOWNLOADS] = value }

    // --- Download Ahead ---

    /** Number of chapters to download ahead while reading. Default: 0 (disabled). */
    val downloadAheadWhileReading: Flow<Int> = dataStore.data.map { it[Keys.DOWNLOAD_AHEAD_WHILE_READING] ?: 0 }
    suspend fun setDownloadAheadWhileReading(value: Int) = dataStore.edit { it[Keys.DOWNLOAD_AHEAD_WHILE_READING] = value }

    /** Only download ahead on Wi-Fi. Default: true. */
    val downloadAheadOnlyOnWifi: Flow<Boolean> = dataStore.data.map { it[Keys.DOWNLOAD_AHEAD_ONLY_ON_WIFI] ?: true }
    suspend fun setDownloadAheadOnlyOnWifi(value: Boolean) = dataStore.edit { it[Keys.DOWNLOAD_AHEAD_ONLY_ON_WIFI] = value }

    // --- Save as CBZ ---

    /**
     * Whether to compress downloaded chapter pages into a CBZ archive.
     * Default: false (loose files are the default for compatibility).
     */
    val saveAsCbz: Flow<Boolean> = dataStore.data.map { it[Keys.SAVE_AS_CBZ] ?: false }
    suspend fun setSaveAsCbz(value: Boolean) = dataStore.edit { it[Keys.SAVE_AS_CBZ] = value }

    // --- Delete After Reading ---
    val deleteAfterReading: Flow<Boolean> = dataStore.data.map { it[Keys.DELETE_AFTER_READING] ?: false }
    suspend fun setDeleteAfterReading(value: Boolean) = dataStore.edit { it[Keys.DELETE_AFTER_READING] = value }

    /**
     * How many of the most recently read chapters to keep downloaded when delete-after-reading
     * is on. 0 = delete the just-read chapter immediately (default); N = delete the chapter N
     * positions earlier in reading order, keeping the last N read chapters on disk.
     */
    val removeAfterReadSlots: Flow<Int> = dataStore.data.map { it[Keys.REMOVE_AFTER_READ_SLOTS] ?: 0 }
    suspend fun setRemoveAfterReadSlots(value: Int) =
        dataStore.edit { it[Keys.REMOVE_AFTER_READ_SLOTS] = value.coerceIn(0, MAX_REMOVE_AFTER_READ_SLOTS) }

    /**
     * Per-manga delete-after-reading overrides stored as "mangaId:MODE" comma-separated string.
     * Example: "123:ENABLED,456:DISABLED"
     */
    val perMangaOverrides: Flow<Map<Long, DeleteAfterReadMode>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.PER_MANGA_OVERRIDES] ?: ""
        raw.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val id = parts[0].toLongOrNull()
                    val mode = try {
                        DeleteAfterReadMode.valueOf(parts[1])
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                    if (id != null && mode != null) id to mode else null
                } else null
            }
            .toMap()
    }

    /**
     * Set a per-manga override for delete-after-reading.
     */
    suspend fun setOverride(mangaId: Long, mode: DeleteAfterReadMode) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PER_MANGA_OVERRIDES] ?: ""
            val overrides = current.split(",")
                .filter { it.isNotBlank() }
                .filterNot { it.startsWith("$mangaId:") }
                .toMutableList()

            if (mode != DeleteAfterReadMode.INHERIT) {
                overrides.add("$mangaId:$mode")
            }

            prefs[Keys.PER_MANGA_OVERRIDES] = overrides.joinToString(",")
        }
    }

    /**
     * Check if delete-after-reading is enabled for a specific manga,
     * taking into account per-manga overrides.
     */
    fun isDeleteAfterReadingEnabled(mangaId: Long): Flow<Boolean> =
        combine(deleteAfterReading, perMangaOverrides) { global, overrides ->
            when (overrides[mangaId]) {
                DeleteAfterReadMode.ENABLED -> true
                DeleteAfterReadMode.DISABLED -> false
                else -> global
            }
        }

    // --- Data Saver ---

    /**
     * When enabled, chapter downloads are blocked while on mobile data to reduce
     * cellular data consumption. Default: false.
     */
    val dataSaverEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.DATA_SAVER_ENABLED] ?: false }
    suspend fun setDataSaverEnabled(value: Boolean) = dataStore.edit { it[Keys.DATA_SAVER_ENABLED] = value }

    // --- Monthly Data Budget ---

    /**
     * User-defined monthly mobile-data budget in megabytes.
     * A value of 0 means "unlimited" (no cap enforced).
     * Exposed as a [Flow] so the data-usage screen can react to changes reactively.
     */
    val monthlyDataBudgetMb: Flow<Int> = dataStore.data.map { it[Keys.MONTHLY_DATA_BUDGET_MB] ?: 0 }
    suspend fun setMonthlyDataBudgetMb(mb: Int) = dataStore.edit { it[Keys.MONTHLY_DATA_BUDGET_MB] = mb }

    // --- Per-Category Auto-Download Filter ---

    /**
     * Allowlist of category IDs for auto-download. When non-empty, only manga belonging to
     * one of these categories will be auto-downloaded during library updates.
     * Empty set (default) means all categories are included.
     *
     * DataStore only supports Set<String>; we store Long IDs as strings and map on read/write.
     */
    val autoDownloadCategoryInclude: Flow<Set<Long>> = dataStore.data.map { prefs ->
        (prefs[Keys.AUTO_DOWNLOAD_CATEGORY_INCLUDE] ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    suspend fun setAutoDownloadCategoryInclude(ids: Set<Long>) = dataStore.edit {
        it[Keys.AUTO_DOWNLOAD_CATEGORY_INCLUDE] = ids.map { id -> id.toString() }.toSet()
    }

    /**
     * Denylist of category IDs for auto-download. Manga belonging to any of these categories
     * will be skipped during auto-download, regardless of the include list.
     * Empty set (default) means no categories are blocked.
     */
    val autoDownloadCategoryExclude: Flow<Set<Long>> = dataStore.data.map { prefs ->
        (prefs[Keys.AUTO_DOWNLOAD_CATEGORY_EXCLUDE] ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    suspend fun setAutoDownloadCategoryExclude(ids: Set<Long>) = dataStore.edit {
        it[Keys.AUTO_DOWNLOAD_CATEGORY_EXCLUDE] = ids.map { id -> id.toString() }.toSet()
    }

    /** Whether to AES-GCM encrypt CBZ archives after creation. Passphrase stored in [CbzEncryptionStore]. */
    val cbzEncryptionEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.CBZ_ENCRYPTION_ENABLED] ?: false }
    suspend fun setCbzEncryptionEnabled(value: Boolean) = dataStore.edit { it[Keys.CBZ_ENCRYPTION_ENABLED] = value }

    // --- Download Folder Names (#1256) ---

    /**
     * The on-disk folder each source's downloads live in, keyed by the `Long` source key.
     *
     * Recorded when `DownloadFolderMigrationWorker` renames a folder, and read back when resolving
     * where a source's chapters are. It exists because the folder name cannot always be re-derived:
     * once an extension is uninstalled there is no display name to resolve, and without a record
     * every already-migrated download under `MangaDex/` would become invisible to the app.
     *
     * Stored as a set of `key:name` entries rather than one delimited string. A set removes any
     * question of a separator appearing inside a folder name, and `:` is safe as the field
     * separator because `DownloadProvider.sanitize` replaces it — so splitting on the first colon
     * can never cut a name in half.
     */
    val sourceFolderNames: Flow<Map<Long, String>> = dataStore.data.map { prefs ->
        prefs[Keys.SOURCE_FOLDER_NAMES].orEmpty().mapNotNull { entry ->
            val key = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
            val name = entry.substringAfter(':', "")
            if (name.isBlank()) null else key to name
        }.toMap()
    }

    /** Records where [sourceId]'s downloads now live. Replaces any previous entry for that key. */
    suspend fun setSourceFolderName(sourceId: Long, folderName: String) {
        dataStore.edit { prefs ->
            val existing = prefs[Keys.SOURCE_FOLDER_NAMES].orEmpty()
                .filterNot { it.substringBefore(':').toLongOrNull() == sourceId }
            prefs[Keys.SOURCE_FOLDER_NAMES] = existing.toSet() + "$sourceId:$folderName"
        }
    }

    private object Keys {
        val SOURCE_FOLDER_NAMES = stringSetPreferencesKey("source_folder_names")
        val AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("auto_download_enabled")
        val DOWNLOAD_ONLY_ON_WIFI = booleanPreferencesKey("download_only_on_wifi")
        val AUTO_DOWNLOAD_LIMIT = intPreferencesKey("auto_download_limit")
        val DOWNLOAD_LOCATION = stringPreferencesKey("download_location")
        val CONCURRENT_DOWNLOADS = intPreferencesKey("concurrent_downloads")
        val DOWNLOAD_AHEAD_WHILE_READING = intPreferencesKey("download_ahead_while_reading")
        val DOWNLOAD_AHEAD_ONLY_ON_WIFI = booleanPreferencesKey("download_ahead_only_on_wifi")
        val SAVE_AS_CBZ = booleanPreferencesKey("save_as_cbz")
        val DELETE_AFTER_READING = booleanPreferencesKey("delete_after_reading")
        val REMOVE_AFTER_READ_SLOTS = intPreferencesKey("remove_after_read_slots")
        val PER_MANGA_OVERRIDES = stringPreferencesKey("delete_after_reading_overrides")
        val DATA_SAVER_ENABLED = booleanPreferencesKey("data_saver_enabled")
        val MONTHLY_DATA_BUDGET_MB = intPreferencesKey("monthly_data_budget_mb")
        val AUTO_DOWNLOAD_CATEGORY_INCLUDE = stringSetPreferencesKey("auto_download_category_include")
        val AUTO_DOWNLOAD_CATEGORY_EXCLUDE = stringSetPreferencesKey("auto_download_category_exclude")
        val CBZ_ENCRYPTION_ENABLED = booleanPreferencesKey("cbz_encryption_enabled")
    }

    companion object {
        /** Upper bound for [removeAfterReadSlots] (matches Komikku's "keep last N" range). */
        const val MAX_REMOVE_AFTER_READ_SLOTS = 4
    }
}
