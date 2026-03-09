package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preference store for download settings including auto-download configuration.
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

    private object Keys {
        val AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("auto_download_enabled")
        val DOWNLOAD_ONLY_ON_WIFI = booleanPreferencesKey("download_only_on_wifi")
        val AUTO_DOWNLOAD_LIMIT = intPreferencesKey("auto_download_limit")
    }
}
