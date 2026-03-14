package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preference store for reading goals, reminders, and streak-related settings.
 * Exposes reactive [Flow] properties and suspend setter functions backed by DataStore.
 */
class ReadingGoalPreferences(private val dataStore: DataStore<Preferences>) {

    // --- Daily goal ---

    /** Daily chapter reading goal (0 = disabled). */
    val dailyChapterGoal: Flow<Int> = dataStore.data.map { it[Keys.DAILY_CHAPTER_GOAL] ?: 0 }
    suspend fun setDailyChapterGoal(value: Int) = dataStore.edit { it[Keys.DAILY_CHAPTER_GOAL] = value.coerceAtLeast(0) }

    // --- Weekly goal ---

    /** Weekly chapter reading goal (0 = disabled). */
    val weeklyChapterGoal: Flow<Int> = dataStore.data.map { it[Keys.WEEKLY_CHAPTER_GOAL] ?: 0 }
    suspend fun setWeeklyChapterGoal(value: Int) = dataStore.edit { it[Keys.WEEKLY_CHAPTER_GOAL] = value.coerceAtLeast(0) }

    // --- Reminders ---

    /** Whether reading reminder notifications are enabled. */
    val remindersEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.REMINDERS_ENABLED] ?: false }
    suspend fun setRemindersEnabled(value: Boolean) = dataStore.edit { it[Keys.REMINDERS_ENABLED] = value }

    /** Hour of the day (0–23) to send the reading reminder. */
    val reminderHour: Flow<Int> = dataStore.data.map { it[Keys.REMINDER_HOUR] ?: 20 }
    suspend fun setReminderHour(value: Int) = dataStore.edit { it[Keys.REMINDER_HOUR] = value.coerceIn(0, 23) }

    private object Keys {
        val DAILY_CHAPTER_GOAL = intPreferencesKey("daily_chapter_goal")
        val WEEKLY_CHAPTER_GOAL = intPreferencesKey("weekly_chapter_goal")
        val REMINDERS_ENABLED = booleanPreferencesKey("reading_reminders_enabled")
        val REMINDER_HOUR = intPreferencesKey("reading_reminder_hour")
    }
}
