package app.otakureader.feature.reader.repository

import app.otakureader.core.preferences.AppPreferences
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReadingDirection
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
    private val preferences: AppPreferences
) {
    // ==================== Reader Mode ====================
    
    val readerMode: Flow<ReaderMode> = preferences.readerMode.map { ordinal ->
        ReaderMode.entries.getOrNull(ordinal) ?: ReaderMode.SINGLE_PAGE
    }
    
    suspend fun setReaderMode(mode: ReaderMode) {
        preferences.setReaderMode(mode.ordinal)
    }
    
    // ==================== Brightness ====================
    
    private val brightnessKey = "reader_brightness"
    val brightness: Flow<Float> = preferences.dataStore.data.map { prefs ->
        prefs[androidx.datastore.preferences.core.floatPreferencesKey(brightnessKey)] ?: 1.0f
    }
    
    suspend fun setBrightness(brightness: Float) {
        preferences.dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.floatPreferencesKey(brightnessKey)] = 
                brightness.coerceIn(0.1f, 1.5f)
        }
    }
    
    // ==================== Zoom Settings ====================
    
    private val zoomLevelKey = "reader_zoom_level"
    val defaultZoom: Flow<Float> = preferences.dataStore.data.map { prefs ->
        prefs[androidx.datastore.preferences.core.floatPreferencesKey(zoomLevelKey)] ?: 1.0f
    }
    
    suspend fun setDefaultZoom(zoom: Float) {
        preferences.dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.floatPreferencesKey(zoomLevelKey)] = 
                zoom.coerceIn(0.5f, 5.0f)
        }
    }
    
    // ==================== Double Tap Zoom ====================
    
    private val doubleTapZoomKey = "reader_double_tap_zoom"
    val doubleTapZoomEnabled: Flow<Boolean> = preferences.dataStore.data.map { prefs ->
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey(doubleTapZoomKey)] ?: true
    }
    
    suspend fun setDoubleTapZoomEnabled(enabled: Boolean) {
        preferences.dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey(doubleTapZoomKey)] = enabled
        }
    }
    
    // ==================== Keep Screen On ====================
    
    val keepScreenOn: Flow<Boolean> = preferences.keepScreenOn
    
    suspend fun setKeepScreenOn(enabled: Boolean) {
        preferences.setKeepScreenOn(enabled)
    }
    
    // ==================== Show Page Number ====================
    
    private val showPageNumberKey = "reader_show_page_number"
    val showPageNumber: Flow<Boolean> = preferences.dataStore.data.map { prefs ->
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey(showPageNumberKey)] ?: true
    }
    
    suspend fun setShowPageNumber(enabled: Boolean) {
        preferences.dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey(showPageNumberKey)] = enabled
        }
    }
    
    // ==================== Reading Direction ====================
    
    private val readingDirectionKey = "reader_direction"
    val readingDirection: Flow<ReadingDirection> = preferences.dataStore.data.map { prefs ->
        val ordinal = prefs[androidx.datastore.preferences.core.intPreferencesKey(readingDirectionKey)] ?: 0
        ReadingDirection.entries.getOrNull(ordinal) ?: ReadingDirection.LTR
    }
    
    suspend fun setReadingDirection(direction: ReadingDirection) {
        preferences.dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.intPreferencesKey(readingDirectionKey)] = direction.ordinal
        }
    }
    
    // ==================== Volume Key Navigation ====================
    
    private val volumeKeyNavKey = "reader_volume_key_nav"
    val volumeKeyNavigation: Flow<Boolean> = preferences.dataStore.data.map { prefs ->
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey(volumeKeyNavKey)] ?: false
    }
    
    suspend fun setVolumeKeyNavigation(enabled: Boolean) {
        preferences.dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey(volumeKeyNavKey)] = enabled
        }
    }
    
    // ==================== Fullscreen ====================
    
    private val fullscreenKey = "reader_fullscreen"
    val fullscreen: Flow<Boolean> = preferences.dataStore.data.map { prefs ->
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey(fullscreenKey)] ?: true
    }
    
    suspend fun setFullscreen(enabled: Boolean) {
        preferences.dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey(fullscreenKey)] = enabled
        }
    }
    
    // ==================== Auto-Scroll Speed ====================
    
    private val autoScrollSpeedKey = "reader_auto_scroll_speed"
    val autoScrollSpeed: Flow<Float> = preferences.dataStore.data.map { prefs ->
        prefs[androidx.datastore.preferences.core.floatPreferencesKey(autoScrollSpeedKey)] ?: 100f
    }
    
    suspend fun setAutoScrollSpeed(speed: Float) {
        preferences.dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.floatPreferencesKey(autoScrollSpeedKey)] = 
                speed.coerceIn(10f, 500f)
        }
    }
    
    // ==================== Tap Zone Configuration ====================
    
    private val tapZoneLeftKey = "reader_tap_zone_left"
    private val tapZoneCenterKey = "reader_tap_zone_center"
    private val tapZoneRightKey = "reader_tap_zone_right"
    
    val tapZoneConfig: Flow<app.otakureader.feature.reader.model.TapZoneConfig> = 
        preferences.dataStore.data.map { prefs ->
            app.otakureader.feature.reader.model.TapZoneConfig(
                leftZoneWidth = prefs[androidx.datastore.preferences.core.floatPreferencesKey(tapZoneLeftKey)] ?: 0.25f,
                centerZoneWidth = prefs[androidx.datastore.preferences.core.floatPreferencesKey(tapZoneCenterKey)] ?: 0.5f,
                rightZoneWidth = prefs[androidx.datastore.preferences.core.floatPreferencesKey(tapZoneRightKey)] ?: 0.25f
            )
        }
    
    suspend fun setTapZoneConfig(config: app.otakureader.feature.reader.model.TapZoneConfig) {
        preferences.dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.floatPreferencesKey(tapZoneLeftKey)] = config.leftZoneWidth
            prefs[androidx.datastore.preferences.core.floatPreferencesKey(tapZoneCenterKey)] = config.centerZoneWidth
            prefs[androidx.datastore.preferences.core.floatPreferencesKey(tapZoneRightKey)] = config.rightZoneWidth
        }
    }
    
    companion object {
        const val DEFAULT_BRIGHTNESS = 1.0f
        const val DEFAULT_ZOOM = 1.0f
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 5.0f
        const val DEFAULT_AUTO_SCROLL_SPEED = 100f
    }
}

// Extension to access dataStore from AppPreferences
private val AppPreferences.dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    get() = javaClass.getDeclaredField("dataStore").let { field ->
        field.isAccessible = true
        field.get(this) as androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    }
