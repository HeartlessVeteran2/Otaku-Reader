package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preference store for scheduled/automatic backup settings.
 * Exposes reactive [Flow] properties and suspend setter functions backed by DataStore.
 */
class BackupPreferences(private val dataStore: DataStore<Preferences>) {

    /** Whether automatic periodic backups are enabled. Default: false. */
    val autoBackupEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_BACKUP_ENABLED] ?: false }
    suspend fun setAutoBackupEnabled(value: Boolean) = dataStore.edit { it[Keys.AUTO_BACKUP_ENABLED] = value }

    /**
     * Backup interval in hours.
     * Supported values: 6, 12, 24, 48, 168 (weekly).
     * Default: 24 hours.
     */
    val autoBackupIntervalHours: Flow<Int> = dataStore.data.map { it[Keys.AUTO_BACKUP_INTERVAL_HOURS] ?: 24 }
    suspend fun setAutoBackupIntervalHours(value: Int) = dataStore.edit { it[Keys.AUTO_BACKUP_INTERVAL_HOURS] = value }

    /**
     * Maximum number of local automatic backup files to retain.
     * Older backups are deleted when the limit is exceeded.
     * Default: 5.
     */
    val autoBackupMaxCount: Flow<Int> = dataStore.data.map { it[Keys.AUTO_BACKUP_MAX_COUNT] ?: 5 }
    suspend fun setAutoBackupMaxCount(value: Int) = dataStore.edit { it[Keys.AUTO_BACKUP_MAX_COUNT] = value }

    private object Keys {
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_INTERVAL_HOURS = intPreferencesKey("auto_backup_interval_hours")
        val AUTO_BACKUP_MAX_COUNT = intPreferencesKey("auto_backup_max_count")
    }
}
