package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preference store for smart notification batching behavior.
 *
 * Controls how aggressively chapter update notifications are grouped, silenced,
 * or batched to avoid notification spam.
 */
class NotificationPreferences(
    private val dataStore: DataStore<Preferences>
) {

    /** Master switch: if false, every manga gets its own notification immediately. */
    val smartBatchingEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_SMART_BATCHING] ?: true }

    /** Minimum minutes between notifications for the *same* manga. */
    val perMangaCooldownMinutes: Flow<Int> = dataStore.data
        .map { it[KEY_PER_MANGA_COOLDOWN] ?: 60 }

    /** Maximum number of individual notifications before forcing a summary. */
    val maxIndividualNotifications: Flow<Int> = dataStore.data
        .map { it[KEY_MAX_INDIVIDUAL] ?: 3 }

    /** Whether to suppress notifications during system quiet hours (respects DND). */
    val respectQuietHours: Flow<Boolean> = dataStore.data
        .map { it[KEY_RESPECT_QUIET] ?: true }

    /**
     * Hide manga titles and covers from update notifications (generic "New chapters available"
     * instead), for privacy on shared or visible lock screens.
     */
    val hideNotificationContent: Flow<Boolean> = dataStore.data
        .map { it[KEY_HIDE_CONTENT] ?: false }

    /** Start hour of quiet period (24h, inclusive). 22 = 10 PM. */
    val quietHoursStart: Flow<Int> = dataStore.data
        .map { it[KEY_QUIET_START] ?: 22 }

    /** End hour of quiet period (24h, exclusive). 8 = 8 AM. */
    val quietHoursEnd: Flow<Int> = dataStore.data
        .map { it[KEY_QUIET_END] ?: 8 }

    // ───────────────────────────────────────────────────────────────────────
    // Setters
    // ───────────────────────────────────────────────────────────────────────

    suspend fun setSmartBatching(enabled: Boolean) {
        dataStore.edit { it[KEY_SMART_BATCHING] = enabled }
    }

    suspend fun setPerMangaCooldownMinutes(minutes: Int) {
        dataStore.edit { it[KEY_PER_MANGA_COOLDOWN] = minutes.coerceIn(15, 720) }
    }

    suspend fun setMaxIndividualNotifications(count: Int) {
        dataStore.edit { it[KEY_MAX_INDIVIDUAL] = count.coerceIn(1, 10) }
    }

    suspend fun setRespectQuietHours(enabled: Boolean) {
        dataStore.edit { it[KEY_RESPECT_QUIET] = enabled }
    }

    suspend fun setHideNotificationContent(enabled: Boolean) {
        dataStore.edit { it[KEY_HIDE_CONTENT] = enabled }
    }

    suspend fun setQuietHours(startHour: Int, endHour: Int) {
        dataStore.edit {
            it[KEY_QUIET_START] = startHour.coerceIn(0, 23)
            it[KEY_QUIET_END] = endHour.coerceIn(0, 23)
        }
    }

    companion object {
        private val KEY_SMART_BATCHING = booleanPreferencesKey("smart_notification_batching")
        private val KEY_PER_MANGA_COOLDOWN = intPreferencesKey("notification_per_manga_cooldown")
        private val KEY_MAX_INDIVIDUAL = intPreferencesKey("notification_max_individual")
        private val KEY_RESPECT_QUIET = booleanPreferencesKey("notification_respect_quiet_hours")
        private val KEY_HIDE_CONTENT = booleanPreferencesKey("notification_hide_content")
        private val KEY_QUIET_START = intPreferencesKey("notification_quiet_start")
        private val KEY_QUIET_END = intPreferencesKey("notification_quiet_end")
    }
}
