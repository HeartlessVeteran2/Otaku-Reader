package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    private object Keys {
        val AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("auto_download_enabled")
        val DOWNLOAD_ONLY_ON_WIFI = booleanPreferencesKey("download_only_on_wifi")
        val AUTO_DOWNLOAD_LIMIT = intPreferencesKey("auto_download_limit")
        val SAVE_AS_CBZ = booleanPreferencesKey("save_as_cbz")
        val DELETE_AFTER_READING = booleanPreferencesKey("delete_after_reading")
        val PER_MANGA_OVERRIDES = stringPreferencesKey("delete_after_reading_overrides")
    }
}
