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
     * Conflict resolution strategy as a stable string name.
     * Stored as the enum name (e.g., "PREFER_NEWER", "PREFER_LOCAL") instead of ordinal
     * to avoid issues when enum order changes.
     *
     * Valid values: "PREFER_NEWER", "PREFER_LOCAL", "PREFER_REMOTE", "MERGE"
     */
    val conflictResolutionStrategy: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.CONFLICT_STRATEGY] ?: "PREFER_NEWER" // Default to PREFER_NEWER
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

    /**
     * Set conflict resolution strategy using a validated strategy name.
     * Valid values: "PREFER_NEWER", "PREFER_LOCAL", "PREFER_REMOTE", "MERGE"
     * Invalid values will default to "PREFER_NEWER".
     */
    suspend fun setConflictResolutionStrategy(strategyName: String) {
        // Validate that the strategy name is valid before storing
        val validStrategies = setOf("PREFER_NEWER", "PREFER_LOCAL", "PREFER_REMOTE", "MERGE")
        val validatedStrategy = if (strategyName in validStrategies) {
            strategyName
        } else {
            "PREFER_NEWER" // Fallback to default if invalid
        }
        dataStore.edit { prefs ->
            prefs[Keys.CONFLICT_STRATEGY] = validatedStrategy
        }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        // Validate that interval is reasonable (at least 1 hour, at most 168 hours/7 days)
        val validatedHours = hours.coerceIn(1, 168)
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_INTERVAL_HOURS] = validatedHours
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

    // Self-hosted server settings (Flow-based for reactive access)
    val selfHostedServerUrlFlow: Flow<String?> = dataStore.data.map { it[Keys.SELF_HOSTED_URL] }
    val selfHostedAuthTokenFlow: Flow<String?> = dataStore.data.map { it[Keys.SELF_HOSTED_TOKEN] }
    val lastSyncTimestampFlow: Flow<Long> = dataStore.data.map { it[Keys.LAST_SYNC_TIMESTAMP] ?: 0L }

    /**
     * Get self-hosted server URL.
     */
    suspend fun getSelfHostedServerUrl(): String =
        selfHostedServerUrlFlow.first() ?: ""

    /**
     * Set self-hosted server URL.
     */
    suspend fun setSelfHostedServerUrl(value: String) {
        dataStore.edit { prefs ->
            if (value.isBlank()) {
                prefs.remove(Keys.SELF_HOSTED_URL)
            } else {
                prefs[Keys.SELF_HOSTED_URL] = value
            }
        }
    }

    /**
     * Get self-hosted auth token.
     */
    suspend fun getSelfHostedAuthToken(): String =
        selfHostedAuthTokenFlow.first() ?: ""

    /**
     * Set self-hosted auth token.
     */
    suspend fun setSelfHostedAuthToken(value: String) {
        dataStore.edit { prefs ->
            if (value.isBlank()) {
                prefs.remove(Keys.SELF_HOSTED_TOKEN)
            } else {
                prefs[Keys.SELF_HOSTED_TOKEN] = value
            }
        }
    }

    /**
     * Get last sync timestamp.
     */
    suspend fun getLastSyncTimestamp(): Long =
        lastSyncTimestampFlow.first()

    /**
     * Set last sync timestamp.
     */
    suspend fun setLastSyncTimestamp(value: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_TIMESTAMP] = value
        }
    }

    private object Keys {
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val PROVIDER_ID = stringPreferencesKey("sync_provider_id")
        val LAST_SYNC_TIME = longPreferencesKey("sync_last_sync_time")
        val DEVICE_ID = stringPreferencesKey("sync_device_id")
        val CONFLICT_STRATEGY = stringPreferencesKey("sync_conflict_strategy")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("sync_auto_enabled")
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        val SYNC_ONLY_WIFI = booleanPreferencesKey("sync_only_wifi")
        val SELF_HOSTED_URL = stringPreferencesKey("self_hosted_url")
        val SELF_HOSTED_TOKEN = stringPreferencesKey("self_hosted_token")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
    }
}
