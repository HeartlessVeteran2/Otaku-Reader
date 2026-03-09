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

    // --- Delete After Reading ---

    /** Global switch: delete downloaded pages after the last page of a chapter is read. Default: false. */
    val deleteAfterReading: Flow<Boolean> = dataStore.data.map { it[Keys.DELETE_AFTER_READING] ?: false }
    suspend fun setDeleteAfterReading(value: Boolean) = dataStore.edit { it[Keys.DELETE_AFTER_READING] = value }

    /**
     * Per-manga overrides for delete-after-reading behaviour, stored as a comma-separated string of
     * `"<mangaId>:<MODE>"` pairs (e.g. `"1:ENABLED,2:DISABLED"`).
     */
    val perMangaOverrides: Flow<Map<Long, DeleteAfterReadMode>> = dataStore.data.map { prefs ->
        deserializeOverrides(prefs[Keys.PER_MANGA_OVERRIDES] ?: "")
    }

    /**
     * Sets a per-manga override.  Passing [DeleteAfterReadMode.INHERIT] removes the entry so the
     * manga falls back to the global setting.
     */
    suspend fun setOverride(mangaId: Long, mode: DeleteAfterReadMode) {
        dataStore.edit { prefs ->
            val current = deserializeOverrides(prefs[Keys.PER_MANGA_OVERRIDES] ?: "").toMutableMap()
            if (mode == DeleteAfterReadMode.INHERIT) {
                current.remove(mangaId)
            } else {
                current[mangaId] = mode
            }
            prefs[Keys.PER_MANGA_OVERRIDES] = serializeOverrides(current)
        }
    }

    /**
     * Returns a [Flow] that emits the effective delete-after-reading setting for [mangaId],
     * resolving the per-manga override against the global flag.
     */
    fun isDeleteAfterReadingEnabled(mangaId: Long): Flow<Boolean> =
        combine(deleteAfterReading, perMangaOverrides) { global, overrides ->
            when (overrides[mangaId] ?: DeleteAfterReadMode.INHERIT) {
                DeleteAfterReadMode.ENABLED -> true
                DeleteAfterReadMode.DISABLED -> false
                DeleteAfterReadMode.INHERIT -> global
            }
        }

    // --- Serialisation helpers ---

    private fun deserializeOverrides(raw: String): Map<Long, DeleteAfterReadMode> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@mapNotNull null
            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            val mode = runCatching { DeleteAfterReadMode.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
            id to mode
        }.toMap()
    }

    private fun serializeOverrides(overrides: Map<Long, DeleteAfterReadMode>): String =
        overrides.entries.joinToString(",") { (id, mode) -> "$id:${mode.name}" }

    private object Keys {
        val AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("auto_download_enabled")
        val DOWNLOAD_ONLY_ON_WIFI = booleanPreferencesKey("download_only_on_wifi")
        val AUTO_DOWNLOAD_LIMIT = intPreferencesKey("auto_download_limit")
        val DELETE_AFTER_READING = booleanPreferencesKey("delete_after_reading")
        val PER_MANGA_OVERRIDES = stringPreferencesKey("per_manga_overrides")
    }
}
