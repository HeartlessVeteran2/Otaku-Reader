package app.otakureader.feature.reader.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.model.TapZoneConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    
    val volumeKeyNavigation: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.VOLUME_KEY_NAV] ?: false
    }
    
    suspend fun setVolumeKeyNavigation(enabled: Boolean) {
        dataStore.edit { it[Keys.VOLUME_KEY_NAV] = enabled }
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

    // ==================== Tap Zones Enabled ====================

    val tapZonesEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.TAP_ZONES_ENABLED] ?: true
    }

    suspend fun setTapZonesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.TAP_ZONES_ENABLED] = enabled }
    }
    
    // ==================== Tap Zone Configuration ====================
    
    val tapZoneConfig: Flow<TapZoneConfig> = dataStore.data.map { prefs ->
        TapZoneConfig(
            leftZoneWidth = prefs[Keys.TAP_ZONE_LEFT] ?: 0.25f,
            centerZoneWidth = prefs[Keys.TAP_ZONE_CENTER] ?: 0.5f,
            rightZoneWidth = prefs[Keys.TAP_ZONE_RIGHT] ?: 0.25f,
            enabled = prefs[Keys.TAP_ZONES_ENABLED] ?: true
        )
    }
    
    suspend fun setTapZoneConfig(config: TapZoneConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.TAP_ZONE_LEFT] = config.leftZoneWidth
            prefs[Keys.TAP_ZONE_CENTER] = config.centerZoneWidth
            prefs[Keys.TAP_ZONE_RIGHT] = config.rightZoneWidth
            prefs[Keys.TAP_ZONES_ENABLED] = config.enabled
        }
    }
    
    private object Keys {
        val READER_MODE = intPreferencesKey("reader_mode_setting")
        val BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val ZOOM_LEVEL = floatPreferencesKey("reader_zoom_level")
        val DOUBLE_TAP_ZOOM = booleanPreferencesKey("reader_double_tap_zoom")
        val KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val SHOW_PAGE_NUMBER = booleanPreferencesKey("reader_show_page_number")
        val READING_DIRECTION = intPreferencesKey("reader_direction")
        val VOLUME_KEY_NAV = booleanPreferencesKey("reader_volume_key_nav")
        val FULLSCREEN = booleanPreferencesKey("reader_fullscreen")
        val AUTO_SCROLL_SPEED = floatPreferencesKey("reader_auto_scroll_speed")
        val TAP_ZONE_LEFT = floatPreferencesKey("reader_tap_zone_left")
        val TAP_ZONE_CENTER = floatPreferencesKey("reader_tap_zone_center")
        val TAP_ZONE_RIGHT = floatPreferencesKey("reader_tap_zone_right")
        val TAP_ZONES_ENABLED = booleanPreferencesKey("reader_tap_zones_enabled")
    }
    
    companion object {
        const val DEFAULT_BRIGHTNESS = 1.0f
        const val DEFAULT_ZOOM = 1.0f
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 5.0f
        const val DEFAULT_AUTO_SCROLL_SPEED = 100f
    }
}
