package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Wrapper around DataStore<Preferences> for type-safe preference access.
 * Exposes reactive [Flow] properties and suspend setter functions.
 * The underlying DataStore is provided via Hilt from [app.otakureader.core.preferences.di.PreferencesModule].
 */
class AppPreferences(private val dataStore: DataStore<Preferences>) {

    // --- Reader settings ---
    val readerMode: Flow<Int> = dataStore.data.map { it[Keys.READER_MODE] ?: 0 }
    suspend fun setReaderMode(value: Int) = dataStore.edit { it[Keys.READER_MODE] = value }

    val readerScale: Flow<Int> = dataStore.data.map { it[Keys.READER_SCALE] ?: 0 }
    suspend fun setReaderScale(value: Int) = dataStore.edit { it[Keys.READER_SCALE] = value }

    val keepScreenOn: Flow<Boolean> = dataStore.data.map { it[Keys.KEEP_SCREEN_ON] ?: true }
    suspend fun setKeepScreenOn(value: Boolean) = dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }

    // --- Library settings ---
    val librarySortMode: Flow<Int> = dataStore.data.map { it[Keys.LIBRARY_SORT_MODE] ?: 0 }
    suspend fun setLibrarySortMode(value: Int) = dataStore.edit { it[Keys.LIBRARY_SORT_MODE] = value }

    val libraryDisplayMode: Flow<Int> = dataStore.data.map { it[Keys.LIBRARY_DISPLAY_MODE] ?: 0 }
    suspend fun setLibraryDisplayMode(value: Int) = dataStore.edit { it[Keys.LIBRARY_DISPLAY_MODE] = value }

    // --- Notifications ---
    val updateCheckInterval: Flow<Int> = dataStore.data.map { it[Keys.UPDATE_CHECK_INTERVAL] ?: 12 }
    suspend fun setUpdateCheckInterval(value: Int) = dataStore.edit { it[Keys.UPDATE_CHECK_INTERVAL] = value }

    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    suspend fun setNotificationsEnabled(value: Boolean) = dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = value }

    // --- Theme ---
    val themeMode: Flow<Int> = dataStore.data.map { it[Keys.THEME_MODE] ?: 0 }
    suspend fun setThemeMode(value: Int) = dataStore.edit { it[Keys.THEME_MODE] = value }

    val useDynamicColor: Flow<Boolean> = dataStore.data.map { it[Keys.USE_DYNAMIC_COLOR] ?: true }
    suspend fun setUseDynamicColor(value: Boolean) = dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = value }

    // --- Migration settings ---

    /** Minimum similarity score (0.0–1.0) to auto-migrate without confirmation. Default: 0.7. */
    val migrationSimilarityThreshold: Flow<Float> = dataStore.data.map {
        it[Keys.MIGRATION_SIMILARITY_THRESHOLD] ?: 0.7f
    }
    suspend fun setMigrationSimilarityThreshold(value: Float) =
        dataStore.edit { it[Keys.MIGRATION_SIMILARITY_THRESHOLD] = value }

    /** When true, always show the confirmation dialog even for high-confidence matches. */
    val migrationAlwaysConfirm: Flow<Boolean> = dataStore.data.map {
        it[Keys.MIGRATION_ALWAYS_CONFIRM] ?: false
    }
    suspend fun setMigrationAlwaysConfirm(value: Boolean) =
        dataStore.edit { it[Keys.MIGRATION_ALWAYS_CONFIRM] = value }

    /** Minimum chapter count a candidate must have to be considered. Default: 0 (no filter). */
    val migrationMinChapterCount: Flow<Int> = dataStore.data.map {
        it[Keys.MIGRATION_MIN_CHAPTER_COUNT] ?: 0
    }
    suspend fun setMigrationMinChapterCount(value: Int) =
        dataStore.edit { it[Keys.MIGRATION_MIN_CHAPTER_COUNT] = value }

    private object Keys {
        val READER_MODE = intPreferencesKey("reader_mode")
        val READER_SCALE = intPreferencesKey("reader_scale")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LIBRARY_SORT_MODE = intPreferencesKey("library_sort_mode")
        val LIBRARY_DISPLAY_MODE = intPreferencesKey("library_display_mode")
        val UPDATE_CHECK_INTERVAL = intPreferencesKey("update_check_interval")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val MIGRATION_SIMILARITY_THRESHOLD = floatPreferencesKey("migration_similarity_threshold")
        val MIGRATION_ALWAYS_CONFIRM = booleanPreferencesKey("migration_always_confirm")
        val MIGRATION_MIN_CHAPTER_COUNT = intPreferencesKey("migration_min_chapter_count")
    }
}
