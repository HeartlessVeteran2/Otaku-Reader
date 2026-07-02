package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

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

    /**
     * Whether to show NSFW (18+) sources and extensions in the Browse screen.
     *
     * Defaults to true to match Mihon/Komikku (showNsfwSource defaults to true). A false default
     * silently hid freshly installed NSFW sources from the Sources screen — the user installs an
     * 18+ extension and it never appears, which reads as a broken install.
     */
    val showNsfwContent: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_NSFW_CONTENT] ?: true }
    suspend fun setShowNsfwContent(value: Boolean) = dataStore.edit { it[Keys.SHOW_NSFW_CONTENT] = value }

    /**
     * Language codes the user wants to see in Browse.
     *
     * Default matches Komikku: "all" (multi-language sources like MangaDex), "en" (English),
     * and the device's current locale language. Without "all", multi-language sources are
     * silently hidden on fresh installs — the user installs MangaDex and it never appears.
     */
    val enabledSourceLanguages: Flow<Set<String>> = dataStore.data.map {
        it[Keys.ENABLED_SOURCE_LANGUAGES] ?: setOf("all", "en", Locale.getDefault().language)
    }
    suspend fun setEnabledSourceLanguages(languages: Set<String>) = dataStore.edit {
        it[Keys.ENABLED_SOURCE_LANGUAGES] = languages
    }

    // --- Discord Rich Presence ---

    /** Whether Discord Rich Presence is enabled. Default: off (opt-in). */
    val discordRpcEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.DISCORD_RPC_ENABLED] ?: false }
    suspend fun setDiscordRpcEnabled(value: Boolean) = dataStore.edit { it[Keys.DISCORD_RPC_ENABLED] = value }

    // --- Onboarding ---

    /** Whether the user has completed onboarding. Default: false (show onboarding on first launch). */
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
    suspend fun setOnboardingCompleted(value: Boolean) = dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }

    /** Display name the user entered during onboarding, used for the Library greeting header. */
    val displayName: Flow<String> = dataStore.data.map { it[Keys.DISPLAY_NAME] ?: "" }
    suspend fun setDisplayName(value: String) = dataStore.edit { it[Keys.DISPLAY_NAME] = value.trim() }

    // --- Auto Theme Color ---

    /**
     * Auto theme color based on manga cover.
     * When enabled, extracts dominant colors from manga cover for dynamic theming.
     */
    val autoThemeColor: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_THEME_COLOR] ?: false }
    suspend fun setAutoThemeColor(value: Boolean) = dataStore.edit { it[Keys.AUTO_THEME_COLOR] = value }

    // --- Visual Effects ---

    /**
     * Whether visual effects (screentone patterns, glassmorphism, neon glows) are enabled.
     * Default: true (effects on).
     */
    val visualEffectsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.VISUAL_EFFECTS_ENABLED] ?: true }
    suspend fun setVisualEffectsEnabled(value: Boolean) = dataStore.edit { it[Keys.VISUAL_EFFECTS_ENABLED] = value }

    // --- Per-Title Content Type Override ---

    /**
     * Returns true when the given manga ID has been overridden to MANHWA (webtoon) content type.
     * Default: false (MANGA).
     */
    fun getMangaContentType(mangaId: Long): Flow<Boolean> = dataStore.data.map { prefs ->
        val ids = prefs[Keys.MANHWA_OVERRIDE_IDS] ?: emptySet()
        mangaId.toString() in ids  // true = MANHWA override, false = MANGA (default)
    }

    /** Adds or removes the given manga ID from the MANHWA override set. */
    suspend fun toggleMangaContentTypeOverride(mangaId: Long, setManhwa: Boolean) = dataStore.edit { prefs ->
        val current = prefs[Keys.MANHWA_OVERRIDE_IDS]?.toMutableSet() ?: mutableSetOf()
        if (setManhwa) current.add(mangaId.toString()) else current.remove(mangaId.toString())
        prefs[Keys.MANHWA_OVERRIDE_IDS] = current
    }

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

    // --- Saved Source Searches (#1051) ---
    // Stored as a raw JSON array string; deserialization to SavedSourceSearch is done by the
    // ViewModel (feature layer) to avoid a domain dependency from this core module.

    /** Raw JSON string of the saved source searches list. */
    val savedSourceSearchesJson: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.SAVED_SOURCE_SEARCHES_JSON] ?: "[]"
    }

    suspend fun setSavedSourceSearchesJson(json: String) = dataStore.edit { prefs ->
        prefs[Keys.SAVED_SOURCE_SEARCHES_JSON] = json
    }

    // --- Browse Search History ---

    val browseSearchHistory: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[Keys.BROWSE_SEARCH_HISTORY]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun addBrowseSearchHistory(query: String) = dataStore.edit { prefs ->
        val normalized = query.trim()
        if (normalized.isBlank()) return@edit
        val current = prefs[Keys.BROWSE_SEARCH_HISTORY]?.split("\n")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        current.remove(normalized)
        current.add(0, normalized)
        prefs[Keys.BROWSE_SEARCH_HISTORY] = current.take(10).joinToString("\n")
    }

    suspend fun clearBrowseSearchHistory() = dataStore.edit { it.remove(Keys.BROWSE_SEARCH_HISTORY) }

    suspend fun removeBrowseSearchHistory(query: String) = dataStore.edit { prefs ->
        val current = prefs[Keys.BROWSE_SEARCH_HISTORY]?.split("\n")?.filter { it.isNotBlank() } ?: return@edit
        val updated = current.filterNot { it == query }
        if (updated.isEmpty()) prefs.remove(Keys.BROWSE_SEARCH_HISTORY)
        else prefs[Keys.BROWSE_SEARCH_HISTORY] = updated.joinToString("\n")
    }

    // --- Browse Filter State (per source) ---

    val browseFilterStates: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[Keys.BROWSE_FILTER_STATES]?.let { decodeFilterStates(it) } ?: emptyMap()
    }

    suspend fun getBrowseFilterState(sourceId: String): String? = browseFilterStates.first()[sourceId]

    suspend fun setBrowseFilterState(sourceId: String, encoded: String?) = dataStore.edit { prefs ->
        val current = prefs[Keys.BROWSE_FILTER_STATES]?.let { decodeFilterStates(it) }?.toMutableMap()
            ?: mutableMapOf()
        if (encoded.isNullOrBlank()) current.remove(sourceId) else current[sourceId] = encoded
        if (current.isEmpty()) prefs.remove(Keys.BROWSE_FILTER_STATES)
        else prefs[Keys.BROWSE_FILTER_STATES] = Json.encodeToString(current)
    }

    private fun decodeFilterStates(raw: String): Map<String, String> =
        runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())

    // --- Security ---

    val biometricLockEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.BIOMETRIC_LOCK_ENABLED] ?: false }
    suspend fun setBiometricLockEnabled(value: Boolean) = dataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = value }

    val biometricLockTimeoutMinutes: Flow<Int> = dataStore.data.map { it[Keys.BIOMETRIC_LOCK_TIMEOUT_MINUTES] ?: 0 }
    suspend fun setBiometricLockTimeoutMinutes(value: Int) =
        dataStore.edit { it[Keys.BIOMETRIC_LOCK_TIMEOUT_MINUTES] = value }

    val biometricLockScheduleEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.BIOMETRIC_LOCK_SCHEDULE_ENABLED] ?: false }
    suspend fun setBiometricLockScheduleEnabled(value: Boolean) =
        dataStore.edit { it[Keys.BIOMETRIC_LOCK_SCHEDULE_ENABLED] = value }

    val biometricLockStartHour: Flow<Int> =
        dataStore.data.map { it[Keys.BIOMETRIC_LOCK_START_HOUR] ?: 22 }
    suspend fun setBiometricLockStartHour(value: Int) =
        dataStore.edit { it[Keys.BIOMETRIC_LOCK_START_HOUR] = value.coerceIn(0, 23) }

    val biometricLockEndHour: Flow<Int> =
        dataStore.data.map { it[Keys.BIOMETRIC_LOCK_END_HOUR] ?: 8 }
    suspend fun setBiometricLockEndHour(value: Int) =
        dataStore.edit { it[Keys.BIOMETRIC_LOCK_END_HOUR] = value.coerceIn(0, 23) }

    val biometricLockActiveDays: Flow<Set<Int>> =
        dataStore.data.map { parseDaySet(it[Keys.BIOMETRIC_LOCK_ACTIVE_DAYS] ?: "") }
    suspend fun setBiometricLockActiveDays(days: Set<Int>) =
        dataStore.edit { it[Keys.BIOMETRIC_LOCK_ACTIVE_DAYS] = encodeDaySet(days) }

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

    // --- Smart Download ---

    val smartDownloadEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SMART_DOWNLOAD_ENABLED] ?: false }
    suspend fun setSmartDownloadEnabled(value: Boolean) = dataStore.edit { it[Keys.SMART_DOWNLOAD_ENABLED] = value }

    val smartDownloadChaptersAhead: Flow<Int> = dataStore.data.map { it[Keys.SMART_DOWNLOAD_CHAPTERS_AHEAD] ?: 3 }
    suspend fun setSmartDownloadChaptersAhead(value: Int) = dataStore.edit { it[Keys.SMART_DOWNLOAD_CHAPTERS_AHEAD] = value }

    val smartDownloadThreshold: Flow<Float> = dataStore.data.map { it[Keys.SMART_DOWNLOAD_THRESHOLD] ?: 0.8f }
    suspend fun setSmartDownloadThreshold(value: Float) = dataStore.edit { it[Keys.SMART_DOWNLOAD_THRESHOLD] = value }

    val smartDownloadWifiOnly: Flow<Boolean> = dataStore.data.map { it[Keys.SMART_DOWNLOAD_WIFI_ONLY] ?: true }
    suspend fun setSmartDownloadWifiOnly(value: Boolean) = dataStore.edit { it[Keys.SMART_DOWNLOAD_WIFI_ONLY] = value }

    val smartDownloadFavoritesOnly: Flow<Boolean> = dataStore.data.map { it[Keys.SMART_DOWNLOAD_FAVORITES_ONLY] ?: true }
    suspend fun setSmartDownloadFavoritesOnly(value: Boolean) = dataStore.edit { it[Keys.SMART_DOWNLOAD_FAVORITES_ONLY] = value }

    val smartDownloadMinStorageMb: Flow<Int> = dataStore.data.map { it[Keys.SMART_DOWNLOAD_MIN_STORAGE_MB] ?: 500 }
    suspend fun setSmartDownloadMinStorageMb(value: Int) = dataStore.edit { it[Keys.SMART_DOWNLOAD_MIN_STORAGE_MB] = value }

    // --- Extension Auto-Update ---

    /** Whether to periodically check for extension updates in the background. */
    val extensionAutoUpdateEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.EXT_AUTO_UPDATE_ENABLED] ?: false }
    suspend fun setExtensionAutoUpdateEnabled(value: Boolean) = dataStore.edit { it[Keys.EXT_AUTO_UPDATE_ENABLED] = value }

    /** Only run the extension update check on unmetered (Wi-Fi) networks. */
    val extensionAutoUpdateWifiOnly: Flow<Boolean> = dataStore.data.map { it[Keys.EXT_AUTO_UPDATE_WIFI_ONLY] ?: true }
    suspend fun setExtensionAutoUpdateWifiOnly(value: Boolean) = dataStore.edit { it[Keys.EXT_AUTO_UPDATE_WIFI_ONLY] = value }

    /** Interval (hours) between background extension update checks. */
    val extensionAutoUpdateIntervalHours: Flow<Int> = dataStore.data.map { it[Keys.EXT_AUTO_UPDATE_INTERVAL] ?: 24 }
    suspend fun setExtensionAutoUpdateIntervalHours(value: Int) = dataStore.edit { it[Keys.EXT_AUTO_UPDATE_INTERVAL] = value }

    // --- Dark Mode Schedule ---

    /** Whether to automatically switch to dark mode on a schedule. */
    val darkModeScheduleEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.DARK_MODE_SCHEDULE_ENABLED] ?: false }
    suspend fun setDarkModeScheduleEnabled(value: Boolean) =
        dataStore.edit { it[Keys.DARK_MODE_SCHEDULE_ENABLED] = value }

    /** Minutes-of-day (0–1439) when dark mode should turn ON. Default: 22:00 (1320). */
    val darkModeStartMinuteOfDay: Flow<Int> = dataStore.data.map { it[Keys.DARK_MODE_START_MINUTE] ?: (22 * 60) }
    suspend fun setDarkModeStartMinuteOfDay(value: Int) =
        dataStore.edit { it[Keys.DARK_MODE_START_MINUTE] = value.coerceIn(0, 1439) }

    /** Minutes-of-day (0–1439) when dark mode should turn OFF. Default: 07:00 (420). */
    val darkModeEndMinuteOfDay: Flow<Int> = dataStore.data.map { it[Keys.DARK_MODE_END_MINUTE] ?: (7 * 60) }
    suspend fun setDarkModeEndMinuteOfDay(value: Int) =
        dataStore.edit { it[Keys.DARK_MODE_END_MINUTE] = value.coerceIn(0, 1439) }

    // --- Last Used Sources ---

    /** Most recently browsed source IDs, capped at 5, most recent first. */
    val lastUsedSourceIds: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_USED_SOURCE_IDS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun recordSourceUsed(sourceId: String) = dataStore.edit { prefs ->
        val current = prefs[Keys.LAST_USED_SOURCE_IDS]?.split("\n")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        current.remove(sourceId)
        current.add(0, sourceId)
        prefs[Keys.LAST_USED_SOURCE_IDS] = current.take(MAX_LAST_USED_SOURCES).joinToString("\n")
    }

    // --- Source Pinning & Categories ---

    /**
     * Set of source IDs that the user has pinned in Browse.
     * Stored as a comma-separated string in DataStore.
     */
    val pinnedSourceIds: Flow<Set<Long>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.PINNED_SOURCE_IDS] ?: return@map emptySet()
        raw.split(",").filter { it.isNotBlank() }.mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    suspend fun setPinnedSourceIds(ids: Set<Long>) = dataStore.edit { prefs ->
        if (ids.isEmpty()) prefs.remove(Keys.PINNED_SOURCE_IDS)
        else prefs[Keys.PINNED_SOURCE_IDS] = ids.joinToString(",")
    }

    suspend fun togglePinnedSource(sourceId: Long) = dataStore.edit { prefs ->
        val current = prefs[Keys.PINNED_SOURCE_IDS]
            ?.split(",")?.filter { it.isNotBlank() }?.mapNotNull { it.trim().toLongOrNull() }?.toMutableSet()
            ?: mutableSetOf()
        if (sourceId in current) current.remove(sourceId) else current.add(sourceId)
        if (current.isEmpty()) prefs.remove(Keys.PINNED_SOURCE_IDS)
        else prefs[Keys.PINNED_SOURCE_IDS] = current.joinToString(",")
    }

    /**
     * Set of source IDs the user has individually hidden from Browse without uninstalling
     * their owning extension — matches Komikku's per-source disable (SourcesFilterScreen /
     * disabledSources preference), distinct from the coarser per-extension enable/disable.
     * Stored as a comma-separated string in DataStore.
     */
    val disabledSourceIds: Flow<Set<Long>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.DISABLED_SOURCE_IDS] ?: return@map emptySet()
        raw.split(",").filter { it.isNotBlank() }.mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    suspend fun toggleDisabledSource(sourceId: Long) = dataStore.edit { prefs ->
        val current = prefs[Keys.DISABLED_SOURCE_IDS]
            ?.split(",")?.filter { it.isNotBlank() }?.mapNotNull { it.trim().toLongOrNull() }?.toMutableSet()
            ?: mutableSetOf()
        if (sourceId in current) current.remove(sourceId) else current.add(sourceId)
        if (current.isEmpty()) prefs.remove(Keys.DISABLED_SOURCE_IDS)
        else prefs[Keys.DISABLED_SOURCE_IDS] = current.joinToString(",")
    }

    /**
     * Map of source ID → user-defined category label.
     * Stored as a JSON object (Map<String, String>) in DataStore.
     */
    val sourceCategoryMap: Flow<Map<Long, String>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.SOURCE_CATEGORY_MAP] ?: return@map emptyMap()
        runCatching {
            Json.decodeFromString<Map<String, String>>(raw)
                .entries.mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    suspend fun setSourceCategory(sourceId: Long, category: String) = dataStore.edit { prefs ->
        val raw = prefs[Keys.SOURCE_CATEGORY_MAP]
        val current: MutableMap<String, String> = raw
            ?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap()) }
            ?.toMutableMap() ?: mutableMapOf()
        if (category.isBlank()) current.remove(sourceId.toString()) else current[sourceId.toString()] = category.trim()
        if (current.isEmpty()) prefs.remove(Keys.SOURCE_CATEGORY_MAP)
        else prefs[Keys.SOURCE_CATEGORY_MAP] = Json.encodeToString(current)
    }

    // --- Extension Signer Hash Continuity ---

    /**
     * Stores the first-seen APK signer hash for each installed extension, keyed by package name.
     * Serialized as a JSON object (Map<String, String>).
     *
     * When a new extension is installed or first seen, its [signatureHash] is recorded here.
     * On subsequent loads the current hash is compared to this value: a change indicates the
     * signing certificate rotated, which is a security-sensitive event worth surfacing to the user.
     */
    val extensionFirstSeenHashes: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[Keys.EXTENSION_FIRST_SEEN_HASHES]?.let { raw ->
            runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrNull() ?: emptyMap()
        } ?: emptyMap()
    }

    /**
     * Records [hash] as the first-seen signer hash for [packageName].
     * No-op if [packageName] is already present — the first-seen value is intentionally
     * immutable once written. If the stored JSON is unreadable the edit is skipped to
     * avoid overwriting the existing trust baseline with a single-entry map.
     */
    suspend fun recordExtensionFirstSeenHash(packageName: String, hash: String) {
        recordExtensionFirstSeenHashes(mapOf(packageName to hash))
    }

    /**
     * Batch variant of [recordExtensionFirstSeenHash] — persists all new entries in a
     * single DataStore edit to avoid N sequential writes on first-open with many extensions.
     */
    suspend fun recordExtensionFirstSeenHashes(hashes: Map<String, String>) {
        if (hashes.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.EXTENSION_FIRST_SEEN_HASHES]?.let { raw ->
                runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrNull()
                    ?: return@edit
            } ?: emptyMap()
            val newEntries = hashes.filterKeys { it !in current }
            if (newEntries.isNotEmpty()) {
                prefs[Keys.EXTENSION_FIRST_SEEN_HASHES] = Json.encodeToString(current + newEntries)
            }
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

    // --- Downloaded Only ---

    val downloadedOnly: Flow<Boolean> = dataStore.data.map { it[Keys.DOWNLOADED_ONLY] ?: false }
    suspend fun setDownloadedOnly(value: Boolean) = dataStore.edit { it[Keys.DOWNLOADED_ONLY] = value }

    private fun parseDaySet(raw: String): Set<Int> =
        raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    private fun encodeDaySet(days: Set<Int>): String = days.joinToString(",")

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
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val AUTO_THEME_COLOR = booleanPreferencesKey("auto_theme_color")
        val VISUAL_EFFECTS_ENABLED = booleanPreferencesKey("visual_effects_enabled")
        val MANHWA_OVERRIDE_IDS = stringSetPreferencesKey("manhwa_override_ids")
        val SAVED_SEARCHES = stringPreferencesKey("saved_searches")
        val APP_UPDATE_CHECK_ENABLED = booleanPreferencesKey("app_update_check_enabled")
        val LAST_APP_UPDATE_CHECK = longPreferencesKey("last_app_update_check")
        val CURRENT_VERSION_CODE = intPreferencesKey("current_version_code")
        val LATEST_VERSION_INFO = stringPreferencesKey("latest_version_info")
        val COIL_DISK_CACHE_SIZE_MB = intPreferencesKey("coil_disk_cache_size_mb")
        val SMART_DOWNLOAD_ENABLED = booleanPreferencesKey("smart_download_enabled")
        val SMART_DOWNLOAD_CHAPTERS_AHEAD = intPreferencesKey("smart_download_chapters_ahead")
        val SMART_DOWNLOAD_THRESHOLD = floatPreferencesKey("smart_download_threshold")
        val SMART_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("smart_download_wifi_only")
        val SMART_DOWNLOAD_FAVORITES_ONLY = booleanPreferencesKey("smart_download_favorites_only")
        val SMART_DOWNLOAD_MIN_STORAGE_MB = intPreferencesKey("smart_download_min_storage_mb")
        val EXT_AUTO_UPDATE_ENABLED = booleanPreferencesKey("extension_auto_update_enabled")
        val EXT_AUTO_UPDATE_WIFI_ONLY = booleanPreferencesKey("extension_auto_update_wifi_only")
        val EXT_AUTO_UPDATE_INTERVAL = intPreferencesKey("extension_auto_update_interval_hours")
        val BROWSE_SEARCH_HISTORY = stringPreferencesKey("browse_search_history")
        val BROWSE_FILTER_STATES = stringPreferencesKey("browse_filter_states")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val BIOMETRIC_LOCK_TIMEOUT_MINUTES = intPreferencesKey("biometric_lock_timeout_minutes")
        val BIOMETRIC_LOCK_SCHEDULE_ENABLED = booleanPreferencesKey("biometric_lock_schedule_enabled")
        val BIOMETRIC_LOCK_START_HOUR = intPreferencesKey("biometric_lock_start_hour")
        val BIOMETRIC_LOCK_END_HOUR = intPreferencesKey("biometric_lock_end_hour")
        val BIOMETRIC_LOCK_ACTIVE_DAYS = stringPreferencesKey("biometric_lock_active_days")
        val DARK_MODE_SCHEDULE_ENABLED = booleanPreferencesKey("dark_mode_schedule_enabled")
        val DARK_MODE_START_MINUTE = intPreferencesKey("dark_mode_start_minute")
        val DARK_MODE_END_MINUTE = intPreferencesKey("dark_mode_end_minute")
        val LAST_USED_SOURCE_IDS = stringPreferencesKey("last_used_source_ids")
        val PINNED_SOURCE_IDS = stringPreferencesKey("pinned_source_ids")
        val DISABLED_SOURCE_IDS = stringPreferencesKey("disabled_source_ids")
        val SOURCE_CATEGORY_MAP = stringPreferencesKey("source_category_map")
        val SAVED_SOURCE_SEARCHES_JSON = stringPreferencesKey("saved_source_searches_json")
        val EXTENSION_FIRST_SEEN_HASHES = stringPreferencesKey("extension_first_seen_hashes")
        val ENABLED_SOURCE_LANGUAGES = stringSetPreferencesKey("enabled_source_languages")
        val DOWNLOADED_ONLY = booleanPreferencesKey("downloaded_only")
    }

    companion object {
        const val DEFAULT_COIL_DISK_CACHE_MB = 512
        const val MIN_COIL_DISK_CACHE_MB = 64
        const val MAX_COIL_DISK_CACHE_MB = 2048
        const val MAX_LAST_USED_SOURCES = 5
    }
}
