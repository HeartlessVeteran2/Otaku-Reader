package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preference store for general application settings including theme and locale.
 * Exposes reactive [Flow] properties and suspend setter functions backed by DataStore.
 */
class GeneralPreferences(private val dataStore: DataStore<Preferences>) {

    // --- Theme ---

    /** Theme mode: 0 = system default, 1 = light, 2 = dark. */
    val themeMode: Flow<Int> = dataStore.data.map { it[Keys.THEME_MODE] ?: 0 }
    suspend fun setThemeMode(value: Int) = dataStore.edit { it[Keys.THEME_MODE] = value }

    /** Whether to use dynamic (Material You) color on Android 12+. */
    val useDynamicColor: Flow<Boolean> = dataStore.data.map { it[Keys.USE_DYNAMIC_COLOR] ?: true }
    suspend fun setUseDynamicColor(value: Boolean) = dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = value }

    /** Whether to use Pure Black (AMOLED) dark mode. */
    val usePureBlackDarkMode: Flow<Boolean> = dataStore.data.map { it[Keys.USE_PURE_BLACK_DARK_MODE] ?: false }
    suspend fun setUsePureBlackDarkMode(value: Boolean) = dataStore.edit { it[Keys.USE_PURE_BLACK_DARK_MODE] = value }

    /**
     * Color scheme selection:
     * 0 = System Default (uses dynamic if available on Android 12+)
     * 1 = Dynamic (Material You - forced on Android 12+)
     * 2 = Green Apple
     * 3 = Lavender
     * 4 = Midnight Dusk
     * 5 = Strawberry Daiquiri
     * 6 = Tako
     * 7 = Teal & Turquoise
     * 8 = Tidal Wave
     * 9 = Yotsuba
     * 10 = Yin & Yang
     */
    val colorScheme: Flow<Int> = dataStore.data.map { it[Keys.COLOR_SCHEME] ?: 0 }
    suspend fun setColorScheme(value: Int) = dataStore.edit { it[Keys.COLOR_SCHEME] = value }

    // --- Locale ---

    /** BCP-47 language tag for the app locale, or empty string to follow the system default. */
    val locale: Flow<String> = dataStore.data.map { it[Keys.LOCALE] ?: "" }
    suspend fun setLocale(value: String) = dataStore.edit { it[Keys.LOCALE] = value }

    // --- Notifications ---

    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    suspend fun setNotificationsEnabled(value: Boolean) = dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = value }

    val updateCheckInterval: Flow<Int> = dataStore.data.map { it[Keys.UPDATE_CHECK_INTERVAL] ?: 12 }
    suspend fun setUpdateCheckInterval(value: Int) = dataStore.edit { it[Keys.UPDATE_CHECK_INTERVAL] = value }

    // --- Browse ---

    /** Whether to show NSFW (18+) sources and extensions in the Browse screen. */
    val showNsfwContent: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_NSFW_CONTENT] ?: false }
    suspend fun setShowNsfwContent(value: Boolean) = dataStore.edit { it[Keys.SHOW_NSFW_CONTENT] = value }

    private object Keys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val USE_PURE_BLACK_DARK_MODE = booleanPreferencesKey("use_pure_black_dark_mode")
        val COLOR_SCHEME = intPreferencesKey("color_scheme")
        val LOCALE = stringPreferencesKey("locale")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val UPDATE_CHECK_INTERVAL = intPreferencesKey("update_check_interval")
        val SHOW_NSFW_CONTENT = booleanPreferencesKey("show_nsfw_content")
    }
}
