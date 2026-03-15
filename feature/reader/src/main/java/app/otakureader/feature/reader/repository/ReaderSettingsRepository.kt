package app.otakureader.feature.reader.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.feature.reader.model.ColorFilterMode
import app.otakureader.feature.reader.model.ImageQuality
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.model.TapZoneConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing reader settings and preferences.
 * Provides type-safe access to all reader-related settings with reactive updates.
 */
@Singleton
class ReaderSettingsRepository @Inject constructor(
    private val preferences: AppPreferences,
    private val dataStore: DataStore<Preferences>
) {
    // ==================== Reader Mode ====================
    
    val readerMode: Flow<ReaderMode> = dataStore.data.map { prefs ->
        val ordinal = prefs[Keys.READER_MODE] ?: 0
        ReaderMode.entries.getOrNull(ordinal) ?: ReaderMode.SINGLE_PAGE
    }
    
    suspend fun setReaderMode(mode: ReaderMode) {
        dataStore.edit { it[Keys.READER_MODE] = mode.ordinal }
    }
    
    // ==================== Brightness ====================
    
    val brightness: Flow<Float> = dataStore.data.map { prefs ->
        prefs[Keys.BRIGHTNESS] ?: DEFAULT_BRIGHTNESS
    }
    
    suspend fun setBrightness(brightness: Float) {
        dataStore.edit { it[Keys.BRIGHTNESS] = brightness.coerceIn(0.1f, 1.5f) }
    }
    
    // ==================== Zoom Settings ====================
    
    val defaultZoom: Flow<Float> = dataStore.data.map { prefs ->
        prefs[Keys.ZOOM_LEVEL] ?: DEFAULT_ZOOM
    }
    
    suspend fun setDefaultZoom(zoom: Float) {
        dataStore.edit { it[Keys.ZOOM_LEVEL] = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM) }
    }
    
    // ==================== Double Tap Zoom ====================
    
    val doubleTapZoomEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DOUBLE_TAP_ZOOM] ?: true
    }
    
    suspend fun setDoubleTapZoomEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DOUBLE_TAP_ZOOM] = enabled }
    }
    
    // ==================== Keep Screen On ====================
    
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.KEEP_SCREEN_ON] ?: true
    }
    
    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }
    
    // ==================== Show Page Number ====================
    
    val showPageNumber: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_PAGE_NUMBER] ?: true
    }
    
    suspend fun setShowPageNumber(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_PAGE_NUMBER] = enabled }
    }
    
    // ==================== Reading Direction ====================
    
    val readingDirection: Flow<ReadingDirection> = dataStore.data.map { prefs ->
        val ordinal = prefs[Keys.READING_DIRECTION] ?: 0
        ReadingDirection.entries.getOrNull(ordinal) ?: ReadingDirection.LTR
    }
    
    suspend fun setReadingDirection(direction: ReadingDirection) {
        dataStore.edit { it[Keys.READING_DIRECTION] = direction.ordinal }
    }
    
    // ==================== Volume Key Navigation ====================
    
    val volumeKeysEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.VOLUME_KEYS_ENABLED] ?: false
    }
    
    suspend fun setVolumeKeysEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VOLUME_KEYS_ENABLED] = enabled }
    }

    val volumeKeysInverted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.VOLUME_KEYS_INVERTED] ?: false
    }

    suspend fun setVolumeKeysInverted(inverted: Boolean) {
        dataStore.edit { it[Keys.VOLUME_KEYS_INVERTED] = inverted }
    }
    
    // ==================== Fullscreen ====================
    
    val fullscreen: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.FULLSCREEN] ?: true
    }
    
    suspend fun setFullscreen(enabled: Boolean) {
        dataStore.edit { it[Keys.FULLSCREEN] = enabled }
    }
    
    // ==================== Auto-Scroll Speed ====================
    
    val autoScrollSpeed: Flow<Float> = dataStore.data.map { prefs ->
        prefs[Keys.AUTO_SCROLL_SPEED] ?: DEFAULT_AUTO_SCROLL_SPEED
    }
    
    suspend fun setAutoScrollSpeed(speed: Float) {
        dataStore.edit { it[Keys.AUTO_SCROLL_SPEED] = speed.coerceIn(10f, 500f) }
    }
    
    // ==================== Tap Zone Configuration ====================

    val tapZoneConfig: Flow<TapZoneConfig> = dataStore.data.map { prefs ->
        TapZoneConfig(
            leftZoneWidth = prefs[Keys.TAP_ZONE_LEFT] ?: 0.25f,
            centerZoneWidth = prefs[Keys.TAP_ZONE_CENTER] ?: 0.5f,
            rightZoneWidth = prefs[Keys.TAP_ZONE_RIGHT] ?: 0.25f
        )
    }

    suspend fun setTapZoneConfig(config: TapZoneConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.TAP_ZONE_LEFT] = config.leftZoneWidth
            prefs[Keys.TAP_ZONE_CENTER] = config.centerZoneWidth
            prefs[Keys.TAP_ZONE_RIGHT] = config.rightZoneWidth
        }
    }

    // ==================== Incognito Mode ====================

    val incognitoMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.INCOGNITO_MODE] ?: false
    }

    suspend fun setIncognitoMode(enabled: Boolean) {
        dataStore.edit { it[Keys.INCOGNITO_MODE] = enabled }
    }

    val colorFilterMode: Flow<ColorFilterMode> = dataStore.data.map { prefs ->
        ColorFilterMode.entries.getOrNull(prefs[Keys.COLOR_FILTER_MODE] ?: 0)
            ?: ColorFilterMode.NONE
    }

    suspend fun setColorFilterMode(mode: ColorFilterMode) {
        dataStore.edit { it[Keys.COLOR_FILTER_MODE] = mode.ordinal }
    }

    val customTintColor: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_TINT_COLOR] ?: 0x4000AAFFL
    }

    suspend fun setCustomTintColor(color: Long) {
        dataStore.edit { it[Keys.CUSTOM_TINT_COLOR] = color }
    }

    // ==================== Page Preloading (#264) ====================

    /** Number of pages to preload before the current page (0–10). */
    val preloadPagesBefore: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.PRELOAD_PAGES_BEFORE] ?: DEFAULT_PRELOAD_PAGES
    }

    suspend fun setPreloadPagesBefore(count: Int) {
        dataStore.edit { it[Keys.PRELOAD_PAGES_BEFORE] = count.coerceIn(0, MAX_PRELOAD_PAGES) }
    }

    /** Number of pages to preload after the current page (0–10). */
    val preloadPagesAfter: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.PRELOAD_PAGES_AFTER] ?: DEFAULT_PRELOAD_PAGES
    }

    suspend fun setPreloadPagesAfter(count: Int) {
        dataStore.edit { it[Keys.PRELOAD_PAGES_AFTER] = count.coerceIn(0, MAX_PRELOAD_PAGES) }
    }

    // ==================== Smart Prefetch Settings ====================

    /** Whether smart prefetch is enabled. */
    val smartPrefetchEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SMART_PREFETCH_ENABLED] ?: true
    }

    suspend fun setSmartPrefetchEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SMART_PREFETCH_ENABLED] = enabled }
    }

    /** Prefetch strategy ordinal (0=Conservative, 1=Balanced, 2=Aggressive, 3=Adaptive). */
    val prefetchStrategyOrdinal: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.PREFETCH_STRATEGY] ?: 1 // Default to Balanced
    }

    suspend fun setPrefetchStrategy(strategyOrdinal: Int) {
        dataStore.edit { it[Keys.PREFETCH_STRATEGY] = strategyOrdinal.coerceIn(0, 3) }
    }

    /** Whether adaptive learning is enabled for prefetch optimization. */
    val adaptiveLearningEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ADAPTIVE_LEARNING_ENABLED] ?: true
    }

    suspend fun setAdaptiveLearningEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ADAPTIVE_LEARNING_ENABLED] = enabled }
    }

    /** Whether to prefetch adjacent chapters. */
    val prefetchAdjacentChapters: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PREFETCH_ADJACENT_CHAPTERS] ?: false
    }

    suspend fun setPrefetchAdjacentChapters(enabled: Boolean) {
        dataStore.edit { it[Keys.PREFETCH_ADJACENT_CHAPTERS] = enabled }
    }

    /** Whether to only prefetch on WiFi (disable on mobile data). */
    val prefetchOnlyOnWiFi: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PREFETCH_ONLY_ON_WIFI] ?: false
    }

    suspend fun setPrefetchOnlyOnWiFi(enabled: Boolean) {
        dataStore.edit { it[Keys.PREFETCH_ONLY_ON_WIFI] = enabled }
    }

    // ==================== Crop Borders ====================

    /** Whether automatic border cropping is enabled during image decoding. */
    val cropBordersEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.CROP_BORDERS_ENABLED] ?: false
    }

    suspend fun setCropBordersEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CROP_BORDERS_ENABLED] = enabled }
    }

    // ==================== Image Quality ====================

    /**
     * Global image quality level for page rendering.
     * Stored as enum name (string) under [Keys.IMAGE_QUALITY] for stability — ordinal-based
     * storage would break if entries were reordered or inserted.
     *
     * Migration: users who previously had an ordinal stored under the old int key
     * ([Keys.IMAGE_QUALITY_LEGACY], name "reader_image_quality") are migrated proactively:
     * on first read the ordinal is converted to the enum name and persisted to the new key,
     * and the legacy key is removed. This ensures the migration happens once and future reads
     * use the stable string key.
     */
    val imageQuality: Flow<ImageQuality> = dataStore.data.map { prefs ->
        val name = prefs[Keys.IMAGE_QUALITY]
        if (name != null) {
            ImageQuality.entries.firstOrNull { it.name == name } ?: ImageQuality.ORIGINAL
        } else {
            // Migrate from legacy ordinal stored under the old int key.
            val legacyOrdinal = prefs[Keys.IMAGE_QUALITY_LEGACY]
            val migratedQuality = ImageQuality.entries.getOrNull(legacyOrdinal ?: 0) ?: ImageQuality.ORIGINAL
            
            // Proactively persist the migrated value (fire-and-forget)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dataStore.edit { editPrefs ->
                        editPrefs[Keys.IMAGE_QUALITY] = migratedQuality.name
                        editPrefs.remove(Keys.IMAGE_QUALITY_LEGACY)
                    }
                } catch (_: Exception) {
                    // Migration failure is non-critical; the in-memory value is still correct
                }
            }
            
            migratedQuality
        }
    }

    suspend fun setImageQuality(quality: ImageQuality) {
        dataStore.edit { prefs ->
            prefs[Keys.IMAGE_QUALITY] = quality.name
            // Remove the legacy int key so future reads always take the new path.
            prefs.remove(Keys.IMAGE_QUALITY_LEGACY)
        }
    }

    // ==================== Data Saver Mode ====================

    /** Whether data saver mode is enabled to reduce image quality and bandwidth usage. */
    val dataSaverEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DATA_SAVER_ENABLED] ?: false
    }

    suspend fun setDataSaverEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DATA_SAVER_ENABLED] = enabled }
    }

    // ==================== Overlay Settings ====================

    /** Whether the reading session timer overlay is shown in the reader. */
    val showReadingTimer: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_READING_TIMER] ?: false
    }

    suspend fun setShowReadingTimer(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_READING_TIMER] = enabled }
    }

    /** Whether the battery/time overlay is shown in the reader. */
    val showBatteryTime: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_BATTERY_TIME] ?: false
    }

    suspend fun setShowBatteryTime(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_BATTERY_TIME] = enabled }
    }

    private object Keys {
        val READER_MODE = intPreferencesKey("reader_mode_setting")
        val BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val ZOOM_LEVEL = floatPreferencesKey("reader_zoom_level")
        val DOUBLE_TAP_ZOOM = booleanPreferencesKey("reader_double_tap_zoom")
        val KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val SHOW_PAGE_NUMBER = booleanPreferencesKey("reader_show_page_number")
        val READING_DIRECTION = intPreferencesKey("reader_direction")
        val VOLUME_KEYS_ENABLED = booleanPreferencesKey("reader_volume_keys_enabled")
        val VOLUME_KEYS_INVERTED = booleanPreferencesKey("reader_volume_keys_inverted")
        val FULLSCREEN = booleanPreferencesKey("reader_fullscreen")
        val AUTO_SCROLL_SPEED = floatPreferencesKey("reader_auto_scroll_speed")
        val TAP_ZONE_LEFT = floatPreferencesKey("reader_tap_zone_left")
        val TAP_ZONE_CENTER = floatPreferencesKey("reader_tap_zone_center")
        val TAP_ZONE_RIGHT = floatPreferencesKey("reader_tap_zone_right")
        val INCOGNITO_MODE = booleanPreferencesKey("reader_incognito_mode")
        val COLOR_FILTER_MODE = intPreferencesKey("reader_color_filter_mode")
        val CUSTOM_TINT_COLOR = longPreferencesKey("reader_custom_tint_color")
        val PRELOAD_PAGES_BEFORE = intPreferencesKey("reader_preload_pages_before")
        val PRELOAD_PAGES_AFTER = intPreferencesKey("reader_preload_pages_after")
        val SMART_PREFETCH_ENABLED = booleanPreferencesKey("reader_smart_prefetch_enabled")
        val PREFETCH_STRATEGY = intPreferencesKey("reader_prefetch_strategy")
        val ADAPTIVE_LEARNING_ENABLED = booleanPreferencesKey("reader_adaptive_learning_enabled")
        val PREFETCH_ADJACENT_CHAPTERS = booleanPreferencesKey("reader_prefetch_adjacent_chapters")
        val PREFETCH_ONLY_ON_WIFI = booleanPreferencesKey("reader_prefetch_only_on_wifi")
        val CROP_BORDERS_ENABLED = booleanPreferencesKey("reader_crop_borders_enabled")
        /**
         * Stable string key – stores the enum entry name (e.g. "HIGH").
         * Uses a distinct preference name ("reader_image_quality_name") from the old int key so
         * the two never collide: DataStore key equality is name-only, meaning a string key and an
         * int key with the same name would be treated as the same key and cause a ClassCastException.
         */
        val IMAGE_QUALITY = stringPreferencesKey("reader_image_quality_name")
        /** Legacy int key kept solely for one-time migration from the old ordinal-based storage. */
        val IMAGE_QUALITY_LEGACY = intPreferencesKey("reader_image_quality")
        val DATA_SAVER_ENABLED = booleanPreferencesKey("reader_data_saver_enabled")
        val SHOW_READING_TIMER = booleanPreferencesKey("reader_show_reading_timer")
        val SHOW_BATTERY_TIME = booleanPreferencesKey("reader_show_battery_time")
    }
    
    companion object {
        const val DEFAULT_BRIGHTNESS = 1.0f
        const val DEFAULT_ZOOM = 1.0f
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 5.0f
        const val DEFAULT_AUTO_SCROLL_SPEED = 100f
        const val DEFAULT_PRELOAD_PAGES = 3
        const val MAX_PRELOAD_PAGES = 10
    }
}
