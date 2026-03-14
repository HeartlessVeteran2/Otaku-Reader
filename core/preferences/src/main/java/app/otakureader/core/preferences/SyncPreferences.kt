package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Preference store for cloud sync settings and metadata.
 */
class SyncPreferences(private val dataStore: DataStore<Preferences>) {

    val isSyncEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SYNC_ENABLED] ?: false }

    val providerId: Flow<String?> = dataStore.data.map { it[Keys.PROVIDER_ID] }

    val lastSyncTime: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNC_TIME]
    }

    val deviceId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.DEVICE_ID]
    }

    /**
     * Conflict resolution strategy as integer ordinal.
     * 0 = PREFER_NEWER (default)
     * 1 = PREFER_LOCAL
     * 2 = PREFER_REMOTE
     * 3 = MERGE
     */
    val conflictResolutionStrategyOrdinal: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.CONFLICT_STRATEGY] ?: 0 // Default to PREFER_NEWER
    }

    val autoSyncEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_SYNC_ENABLED] ?: false }

    val syncIntervalHours: Flow<Int> = dataStore.data.map { it[Keys.SYNC_INTERVAL_HOURS] ?: 24 }

    val syncOnlyOnWifi: Flow<Boolean> = dataStore.data.map { it[Keys.SYNC_ONLY_WIFI] ?: true }

    suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_ENABLED] = enabled
        }
    }

    suspend fun setProvider(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(Keys.PROVIDER_ID)
            } else {
                prefs[Keys.PROVIDER_ID] = id
            }
        }
    }

    suspend fun setLastSyncTime(timestamp: Long?) {
        dataStore.edit { prefs ->
            if (timestamp == null) {
                prefs.remove(Keys.LAST_SYNC_TIME)
            } else {
                prefs[Keys.LAST_SYNC_TIME] = timestamp
            }
        }
    }

    suspend fun setConflictResolutionStrategyOrdinal(ordinal: Int) {
        val clamped = ordinal.coerceAtLeast(0)
        dataStore.edit { prefs ->
            prefs[Keys.CONFLICT_STRATEGY] = clamped
        }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        val clamped = hours.coerceAtLeast(1)
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_INTERVAL_HOURS] = clamped
        }
    }

    suspend fun setSyncOnlyOnWifi(onlyWifi: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_ONLY_WIFI] = onlyWifi
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = deviceId.first()
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            prefs[Keys.DEVICE_ID] = newId
        }
        return newId
    }

    suspend fun clearMetadata() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.PROVIDER_ID)
            prefs.remove(Keys.LAST_SYNC_TIME)
        }
    }

    private object Keys {
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val PROVIDER_ID = stringPreferencesKey("sync_provider_id")
        val LAST_SYNC_TIME = longPreferencesKey("sync_last_sync_time")
        val DEVICE_ID = stringPreferencesKey("sync_device_id")
        val CONFLICT_STRATEGY = intPreferencesKey("sync_conflict_strategy")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("sync_auto_enabled")
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        val SYNC_ONLY_WIFI = booleanPreferencesKey("sync_only_wifi")
    }
}
