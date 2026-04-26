package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preference store for reader-related settings such as reading mode, keep-screen-on, and scale.
 * Exposes reactive [Flow] properties and suspend setter functions backed by DataStore.
 */
class ReaderPreferences(private val dataStore: DataStore<Preferences>) {

    // --- Reading Mode ---

    /** Reader display mode ordinal — matches [app.otakureader.domain.model.ReaderMode]:
     *  0 = SINGLE_PAGE, 1 = DUAL_PAGE, 2 = WEBTOON, 3 = SMART_PANELS. */
    val readerMode: Flow<Int> = dataStore.data.map { it[Keys.READER_MODE] ?: 0 }
    suspend fun setReaderMode(value: Int) = dataStore.edit { it[Keys.READER_MODE] = value }

    // --- Screen ---

    val keepScreenOn: Flow<Boolean> = dataStore.data.map { it[Keys.KEEP_SCREEN_ON] ?: true }
    suspend fun setKeepScreenOn(value: Boolean) = dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }

    val fullscreen: Flow<Boolean> = dataStore.data.map { it[Keys.FULLSCREEN] ?: true }
    suspend fun setFullscreen(value: Boolean) = dataStore.edit { it[Keys.FULLSCREEN] = value }

    val showContentInCutout: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_CONTENT_IN_CUTOUT] ?: true }
    suspend fun setShowContentInCutout(value: Boolean) = dataStore.edit { it[Keys.SHOW_CONTENT_IN_CUTOUT] = value }

    // --- Display ---

    val showPageNumber: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_PAGE_NUMBER] ?: true }
    suspend fun setShowPageNumber(value: Boolean) = dataStore.edit { it[Keys.SHOW_PAGE_NUMBER] = value }

    /** Background color for reader: 0 = Black, 1 = White, 2 = Gray, 3 = Auto */
    val backgroundColor: Flow<Int> = dataStore.data.map { it[Keys.BACKGROUND_COLOR] ?: 0 }
    suspend fun setBackgroundColor(value: Int) = dataStore.edit { it[Keys.BACKGROUND_COLOR] = value }

    val animatePageTransitions: Flow<Boolean> = dataStore.data.map { it[Keys.ANIMATE_PAGE_TRANSITIONS] ?: true }
    suspend fun setAnimatePageTransitions(value: Boolean) = dataStore.edit { it[Keys.ANIMATE_PAGE_TRANSITIONS] = value }

    /** Double tap animation speed: 0 = Slow, 1 = Normal, 2 = Fast */
    val doubleTapAnimationSpeed: Flow<Int> = dataStore.data.map { it[Keys.DOUBLE_TAP_ANIMATION_SPEED] ?: 1 }
    suspend fun setDoubleTapAnimationSpeed(value: Int) = dataStore.edit { it[Keys.DOUBLE_TAP_ANIMATION_SPEED] = value }

    val showReadingModeOverlay: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_READING_MODE_OVERLAY] ?: true }
    suspend fun setShowReadingModeOverlay(value: Boolean) = dataStore.edit { it[Keys.SHOW_READING_MODE_OVERLAY] = value }

    val showTapZonesOverlay: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_TAP_ZONES_OVERLAY] ?: false }
    suspend fun setShowTapZonesOverlay(value: Boolean) = dataStore.edit { it[Keys.SHOW_TAP_ZONES_OVERLAY] = value }

    // --- Scale ---

    /** Scale type: 0 = Fit Screen, 1 = Fit Width, 2 = Fit Height, 3 = Original, 4 = Smart Fit */
    val readerScale: Flow<Int> = dataStore.data.map { it[Keys.READER_SCALE] ?: 0 }
    suspend fun setReaderScale(value: Int) = dataStore.edit { it[Keys.READER_SCALE] = value }

    val autoZoomWideImages: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_ZOOM_WIDE_IMAGES] ?: true }
    suspend fun setAutoZoomWideImages(value: Boolean) = dataStore.edit { it[Keys.AUTO_ZOOM_WIDE_IMAGES] = value }

    // --- Tap Zones ---

    /** Tap zone configuration: 0 = Default, 1 = Left-handed, 2 = Kindle, 3 = Edge */
    val tapZoneConfig: Flow<Int> = dataStore.data.map { it[Keys.TAP_ZONE_CONFIG] ?: 0 }
    suspend fun setTapZoneConfig(value: Int) = dataStore.edit { it[Keys.TAP_ZONE_CONFIG] = value }

    val invertTapZones: Flow<Boolean> = dataStore.data.map { it[Keys.INVERT_TAP_ZONES] ?: false }
    suspend fun setInvertTapZones(value: Boolean) = dataStore.edit { it[Keys.INVERT_TAP_ZONES] = value }

    // --- Volume keys ---

    val volumeKeysEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.VOLUME_KEYS_ENABLED] ?: false }
    suspend fun setVolumeKeysEnabled(enabled: Boolean) = dataStore.edit { it[Keys.VOLUME_KEYS_ENABLED] = enabled }

    val volumeKeysInverted: Flow<Boolean> = dataStore.data.map { it[Keys.VOLUME_KEYS_INVERTED] ?: false }
    suspend fun setVolumeKeysInverted(inverted: Boolean) = dataStore.edit { it[Keys.VOLUME_KEYS_INVERTED] = inverted }

    // --- Auto Webtoon Detection ---

    /** Automatically detect webtoon/long-strip manga and switch to webtoon mode */
    val autoWebtoonDetection: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_WEBTOON_DETECTION] ?: true }
    suspend fun setAutoWebtoonDetection(enabled: Boolean) = dataStore.edit { it[Keys.AUTO_WEBTOON_DETECTION] = enabled }

    /** Image aspect ratio threshold for webtoon detection (height/width > this = webtoon) */
    val webtoonDetectionThreshold: Flow<Float> = dataStore.data.map { it[Keys.WEBTOON_THRESHOLD]?.let { it / 100f } ?: 1.5f }
    suspend fun setWebtoonDetectionThreshold(value: Float) = dataStore.edit { it[Keys.WEBTOON_THRESHOLD] = (value * 100).toInt() }

    // --- Page Preload Customization ---

    /** Number of pages to preload before current page (0 = disable) */
    val preloadPagesBefore: Flow<Int> = dataStore.data.map { it[Keys.PRELOAD_PAGES_BEFORE] ?: 2 }
    suspend fun setPreloadPagesBefore(value: Int) = dataStore.edit { it[Keys.PRELOAD_PAGES_BEFORE] = value }

    /** Number of pages to preload after current page (0 = disable) */
    val preloadPagesAfter: Flow<Int> = dataStore.data.map { it[Keys.PRELOAD_PAGES_AFTER] ?: 3 }
    suspend fun setPreloadPagesAfter(value: Int) = dataStore.edit { it[Keys.PRELOAD_PAGES_AFTER] = value }

    // --- Smart Background ---

    /** Enable smart background that adapts to page colors */
    val smartBackground: Flow<Boolean> = dataStore.data.map { it[Keys.SMART_BACKGROUND] ?: false }
    suspend fun setSmartBackground(enabled: Boolean) = dataStore.edit { it[Keys.SMART_BACKGROUND] = enabled }

    // --- Auto Theme Color ---

    /** Enable automatic theme color extraction from manga covers */
    val autoThemeColor: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_THEME_COLOR] ?: true }
    suspend fun setAutoThemeColor(enabled: Boolean) = dataStore.edit { it[Keys.AUTO_THEME_COLOR] = enabled }

    // --- Force Disable Webtoon Zoom ---

    /** Disable zoom gestures in webtoon mode for smoother scrolling */
    val forceDisableWebtoonZoom: Flow<Boolean> = dataStore.data.map { it[Keys.FORCE_DISABLE_WEBTOON_ZOOM] ?: false }
    suspend fun setForceDisableWebtoonZoom(enabled: Boolean) = dataStore.edit { it[Keys.FORCE_DISABLE_WEBTOON_ZOOM] = enabled }

    // --- Webtoon Settings ---

    /** Side padding for webtoon mode: 0 = None, 1 = Small, 2 = Medium, 3 = Large */
    val webtoonSidePadding: Flow<Int> = dataStore.data.map { it[Keys.WEBTOON_SIDE_PADDING] ?: 0 }
    suspend fun setWebtoonSidePadding(value: Int) = dataStore.edit { it[Keys.WEBTOON_SIDE_PADDING] = value }

    /** Menu hide sensitivity for webtoon: 0 = Low, 1 = Medium, 2 = High */
    val webtoonMenuHideSensitivity: Flow<Int> = dataStore.data.map { it[Keys.WEBTOON_MENU_HIDE_SENSITIVITY] ?: 0 }
    suspend fun setWebtoonMenuHideSensitivity(value: Int) = dataStore.edit { it[Keys.WEBTOON_MENU_HIDE_SENSITIVITY] = value }

    val webtoonDoubleTapZoom: Flow<Boolean> = dataStore.data.map { it[Keys.WEBTOON_DOUBLE_TAP_ZOOM] ?: true }
    suspend fun setWebtoonDoubleTapZoom(value: Boolean) = dataStore.edit { it[Keys.WEBTOON_DOUBLE_TAP_ZOOM] = value }

    val webtoonDisableZoomOut: Flow<Boolean> = dataStore.data.map { it[Keys.WEBTOON_DISABLE_ZOOM_OUT] ?: false }
    suspend fun setWebtoonDisableZoomOut(value: Boolean) = dataStore.edit { it[Keys.WEBTOON_DISABLE_ZOOM_OUT] = value }

    // --- E-ink Display Settings ---

    /** Flash screen on page change to reduce ghosting on E-ink displays */
    val einkFlashOnPageChange: Flow<Boolean> = dataStore.data.map { it[Keys.EINK_FLASH_ON_PAGE_CHANGE] ?: false }
    suspend fun setEinkFlashOnPageChange(value: Boolean) = dataStore.edit { it[Keys.EINK_FLASH_ON_PAGE_CHANGE] = value }

    /** Black and white mode for E-ink displays */
    val einkBlackAndWhite: Flow<Boolean> = dataStore.data.map { it[Keys.EINK_BLACK_AND_WHITE] ?: false }
    suspend fun setEinkBlackAndWhite(value: Boolean) = dataStore.edit { it[Keys.EINK_BLACK_AND_WHITE] = value }

    // --- Reading Behavior ---

    val skipReadChapters: Flow<Boolean> = dataStore.data.map { it[Keys.SKIP_READ_CHAPTERS] ?: false }
    suspend fun setSkipReadChapters(value: Boolean) = dataStore.edit { it[Keys.SKIP_READ_CHAPTERS] = value }

    val skipFilteredChapters: Flow<Boolean> = dataStore.data.map { it[Keys.SKIP_FILTERED_CHAPTERS] ?: true }
    suspend fun setSkipFilteredChapters(value: Boolean) = dataStore.edit { it[Keys.SKIP_FILTERED_CHAPTERS] = value }

    val skipDuplicateChapters: Flow<Boolean> = dataStore.data.map { it[Keys.SKIP_DUPLICATE_CHAPTERS] ?: false }
    suspend fun setSkipDuplicateChapters(value: Boolean) = dataStore.edit { it[Keys.SKIP_DUPLICATE_CHAPTERS] = value }

    val alwaysShowChapterTransition: Flow<Boolean> = dataStore.data.map { it[Keys.ALWAYS_SHOW_CHAPTER_TRANSITION] ?: true }
    suspend fun setAlwaysShowChapterTransition(value: Boolean) = dataStore.edit { it[Keys.ALWAYS_SHOW_CHAPTER_TRANSITION] = value }

    // --- Actions ---

    val showActionsOnLongTap: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_ACTIONS_ON_LONG_TAP] ?: true }
    suspend fun setShowActionsOnLongTap(value: Boolean) = dataStore.edit { it[Keys.SHOW_ACTIONS_ON_LONG_TAP] = value }

    val savePagesToSeparateFolders: Flow<Boolean> = dataStore.data.map { it[Keys.SAVE_PAGES_TO_SEPARATE_FOLDERS] ?: false }
    suspend fun setSavePagesToSeparateFolders(value: Boolean) = dataStore.edit { it[Keys.SAVE_PAGES_TO_SEPARATE_FOLDERS] = value }

    private object Keys {
        val READER_MODE = intPreferencesKey("reader_mode_setting")
        val KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val FULLSCREEN = booleanPreferencesKey("reader_fullscreen")
        val SHOW_CONTENT_IN_CUTOUT = booleanPreferencesKey("reader_show_content_in_cutout")
        val SHOW_PAGE_NUMBER = booleanPreferencesKey("reader_show_page_number")
        val BACKGROUND_COLOR = intPreferencesKey("reader_background_color")
        val ANIMATE_PAGE_TRANSITIONS = booleanPreferencesKey("reader_animate_page_transitions")
        val DOUBLE_TAP_ANIMATION_SPEED = intPreferencesKey("reader_double_tap_animation_speed")
        val SHOW_READING_MODE_OVERLAY = booleanPreferencesKey("reader_show_reading_mode_overlay")
        val SHOW_TAP_ZONES_OVERLAY = booleanPreferencesKey("reader_show_tap_zones_overlay")
        val READER_SCALE = intPreferencesKey("reader_scale")
        val AUTO_ZOOM_WIDE_IMAGES = booleanPreferencesKey("reader_auto_zoom_wide_images")
        val TAP_ZONE_CONFIG = intPreferencesKey("reader_tap_zone_config")
        val INVERT_TAP_ZONES = booleanPreferencesKey("reader_invert_tap_zones")
        val VOLUME_KEYS_ENABLED = booleanPreferencesKey("reader_volume_keys_enabled")
        val VOLUME_KEYS_INVERTED = booleanPreferencesKey("reader_volume_keys_inverted")
        val AUTO_WEBTOON_DETECTION = booleanPreferencesKey("reader_auto_webtoon_detection")
        val WEBTOON_THRESHOLD = intPreferencesKey("reader_webtoon_threshold")
        val PRELOAD_PAGES_BEFORE = intPreferencesKey("reader_preload_pages_before")
        val PRELOAD_PAGES_AFTER = intPreferencesKey("reader_preload_pages_after")
        val SMART_BACKGROUND = booleanPreferencesKey("reader_smart_background")
        val AUTO_THEME_COLOR = booleanPreferencesKey("reader_auto_theme_color")
        val FORCE_DISABLE_WEBTOON_ZOOM = booleanPreferencesKey("reader_force_disable_webtoon_zoom")
        val WEBTOON_SIDE_PADDING = intPreferencesKey("reader_webtoon_side_padding")
        val WEBTOON_MENU_HIDE_SENSITIVITY = intPreferencesKey("reader_webtoon_menu_hide_sensitivity")
        val WEBTOON_DOUBLE_TAP_ZOOM = booleanPreferencesKey("reader_webtoon_double_tap_zoom")
        val WEBTOON_DISABLE_ZOOM_OUT = booleanPreferencesKey("reader_webtoon_disable_zoom_out")
        val EINK_FLASH_ON_PAGE_CHANGE = booleanPreferencesKey("reader_eink_flash_on_page_change")
        val EINK_BLACK_AND_WHITE = booleanPreferencesKey("reader_eink_black_and_white")
        val SKIP_READ_CHAPTERS = booleanPreferencesKey("reader_skip_read_chapters")
        val SKIP_FILTERED_CHAPTERS = booleanPreferencesKey("reader_skip_filtered_chapters")
        val SKIP_DUPLICATE_CHAPTERS = booleanPreferencesKey("reader_skip_duplicate_chapters")
        val ALWAYS_SHOW_CHAPTER_TRANSITION = booleanPreferencesKey("reader_always_show_chapter_transition")
        val SHOW_ACTIONS_ON_LONG_TAP = booleanPreferencesKey("reader_show_actions_on_long_tap")
        val SAVE_PAGES_TO_SEPARATE_FOLDERS = booleanPreferencesKey("reader_save_pages_to_separate_folders")
    }
}
