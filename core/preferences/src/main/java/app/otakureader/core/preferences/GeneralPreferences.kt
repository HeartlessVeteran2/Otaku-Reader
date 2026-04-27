package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

    /** Whether to use high-contrast colors for improved accessibility. */
    val useHighContrast: Flow<Boolean> = dataStore.data.map { it[Keys.USE_HIGH_CONTRAST] ?: false }
    suspend fun setUseHighContrast(value: Boolean) = dataStore.edit { it[Keys.USE_HIGH_CONTRAST] = value }

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

    /**
     * Custom accent color stored as an ARGB Long.
     * Used when colorScheme == COLOR_SCHEME_CUSTOM_ACCENT ("Custom") to generate a personalized color scheme.
     * Default is Material Blue (0xFF1976D2).
     */
    val customAccentColor: Flow<Long> = dataStore.data.map { it[Keys.CUSTOM_ACCENT_COLOR] ?: 0xFF1976D2L }
    suspend fun setCustomAccentColor(value: Long) = dataStore.edit { it[Keys.CUSTOM_ACCENT_COLOR] = value }

    // --- Locale ---

    /** BCP-47 language tag for the app locale, or empty string to follow the system default. */
    val locale: Flow<String> = dataStore.data.map { it[Keys.LOCALE] ?: "" }
    suspend fun setLocale(value: String) = dataStore.edit { it[Keys.LOCALE] = value }

    // --- Notifications ---

    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    suspend fun setNotificationsEnabled(value: Boolean) = dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = value }

    val updateCheckInterval: Flow<Int> = dataStore.data.map { it[Keys.UPDATE_CHECK_INTERVAL] ?: 12 }
    suspend fun setUpdateCheckInterval(value: Int) = dataStore.edit { it[Keys.UPDATE_CHECK_INTERVAL] = value }

    /** Epoch-millis timestamp of when the user last viewed the Updates screen. Used for badge counting. */
    val lastUpdatesViewedAt: Flow<Long> = dataStore.data.map { it[Keys.LAST_UPDATES_VIEWED_AT] ?: 0L }
    suspend fun setLastUpdatesViewedAt(value: Long) = dataStore.edit { it[Keys.LAST_UPDATES_VIEWED_AT] = value }

    // --- Browse ---

    /** Whether to show NSFW (18+) sources and extensions in the Browse screen. */
    val showNsfwContent: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_NSFW_CONTENT] ?: false }
    suspend fun setShowNsfwContent(value: Boolean) = dataStore.edit { it[Keys.SHOW_NSFW_CONTENT] = value }

    // --- Discord Rich Presence ---

    /** Whether Discord Rich Presence is enabled. Default: off (opt-in). */
    val discordRpcEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.DISCORD_RPC_ENABLED] ?: false }
    suspend fun setDiscordRpcEnabled(value: Boolean) = dataStore.edit { it[Keys.DISCORD_RPC_ENABLED] = value }

    // --- Onboarding ---

    /** Whether the user has completed onboarding. Default: false (show onboarding on first launch). */
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
    suspend fun setOnboardingCompleted(value: Boolean) = dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }

    // --- Auto Theme Color ---

    /**
     * Auto theme color based on manga cover.
     * When enabled, extracts dominant colors from manga cover for dynamic theming.
     */
    val autoThemeColor: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_THEME_COLOR] ?: false }
    suspend fun setAutoThemeColor(value: Boolean) = dataStore.edit { it[Keys.AUTO_THEME_COLOR] = value }

    // --- Saved Searches ---

    /**
     * Saved search queries for quick access in Browse screen.
     * Stored as comma-separated list.
     */
    val savedSearches: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.SAVED_SEARCHES]?.split("\n")?.toSet() ?: emptySet()
    }
    
    suspend fun addSavedSearch(query: String) = dataStore.edit { prefs ->
        val current = prefs[Keys.SAVED_SEARCHES]?.split("\n")?.toMutableSet() ?: mutableSetOf()
        current.add(query)
        prefs[Keys.SAVED_SEARCHES] = current.joinToString("\n")
    }
    
    suspend fun removeSavedSearch(query: String) = dataStore.edit { prefs ->
        val current = prefs[Keys.SAVED_SEARCHES]?.split("\n")?.toMutableSet() ?: mutableSetOf()
        current.remove(query)
        prefs[Keys.SAVED_SEARCHES] = current.joinToString("\n")
    }

    // --- App Update Checker ---

    /** Whether automatic app update checking is enabled. */
    val appUpdateCheckEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.APP_UPDATE_CHECK_ENABLED] ?: true }
    suspend fun setAppUpdateCheckEnabled(value: Boolean) = dataStore.edit { it[Keys.APP_UPDATE_CHECK_ENABLED] = value }

    /** Last time app update was checked (epoch millis). */
    val lastAppUpdateCheck: Flow<Long> = dataStore.data.map { it[Keys.LAST_APP_UPDATE_CHECK] ?: 0L }
    suspend fun setLastAppUpdateCheck(value: Long) = dataStore.edit { it[Keys.LAST_APP_UPDATE_CHECK] = value }

    /** Currently installed app version code. */
    val currentVersionCode: Flow<Int> = dataStore.data.map { it[Keys.CURRENT_VERSION_CODE] ?: 0 }
    suspend fun setCurrentVersionCode(value: Int) = dataStore.edit { it[Keys.CURRENT_VERSION_CODE] = value }

    /** Latest available version info (stored as JSON string). */
    val latestVersionInfo: Flow<String?> = dataStore.data.map { it[Keys.LATEST_VERSION_INFO] }
    suspend fun setLatestVersionInfo(value: String?) = dataStore.edit {
        if (value != null) {
            it[Keys.LATEST_VERSION_INFO] = value
        } else {
            it.remove(Keys.LATEST_VERSION_INFO)
        }
    }

    // --- Image Cache ---

    /**
     * Maximum size of Coil's on-disk image cache in megabytes.
     * Changes take effect after the next app restart.
     */
    val coilDiskCacheSizeMb: Flow<Int> =
        dataStore.data.map { it[Keys.COIL_DISK_CACHE_SIZE_MB] ?: DEFAULT_COIL_DISK_CACHE_MB }
    suspend fun setCoilDiskCacheSizeMb(value: Int) =
        dataStore.edit { it[Keys.COIL_DISK_CACHE_SIZE_MB] = value.coerceIn(MIN_COIL_DISK_CACHE_MB, MAX_COIL_DISK_CACHE_MB) }

    private object Keys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val USE_PURE_BLACK_DARK_MODE = booleanPreferencesKey("use_pure_black_dark_mode")
        val USE_HIGH_CONTRAST = booleanPreferencesKey("use_high_contrast")
        val COLOR_SCHEME = intPreferencesKey("color_scheme")
        val CUSTOM_ACCENT_COLOR = longPreferencesKey("custom_accent_color")
        val LOCALE = stringPreferencesKey("locale")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val UPDATE_CHECK_INTERVAL = intPreferencesKey("update_check_interval")
        val LAST_UPDATES_VIEWED_AT = longPreferencesKey("last_updates_viewed_at")
        val SHOW_NSFW_CONTENT = booleanPreferencesKey("show_nsfw_content")
        val DISCORD_RPC_ENABLED = booleanPreferencesKey("discord_rpc_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val AUTO_THEME_COLOR = booleanPreferencesKey("auto_theme_color")
        val SAVED_SEARCHES = stringPreferencesKey("saved_searches")
        val APP_UPDATE_CHECK_ENABLED = booleanPreferencesKey("app_update_check_enabled")
        val LAST_APP_UPDATE_CHECK = longPreferencesKey("last_app_update_check")
        val CURRENT_VERSION_CODE = intPreferencesKey("current_version_code")
        val LATEST_VERSION_INFO = stringPreferencesKey("latest_version_info")
        val COIL_DISK_CACHE_SIZE_MB = intPreferencesKey("coil_disk_cache_size_mb")
    }

    companion object {
        const val DEFAULT_COIL_DISK_CACHE_MB = 512
        const val MIN_COIL_DISK_CACHE_MB = 64
        const val MAX_COIL_DISK_CACHE_MB = 2048
    }
}
