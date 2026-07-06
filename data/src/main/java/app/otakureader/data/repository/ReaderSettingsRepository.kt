package app.otakureader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otakureader.core.common.di.ApplicationScope
import app.otakureader.domain.model.ColorFilterMode
import app.otakureader.domain.repository.ReaderSettingsRepository as ReaderSettingsRepositoryInterface
import app.otakureader.domain.model.ImageQuality
import app.otakureader.domain.model.ReaderMode
import app.otakureader.domain.model.ReaderOrientation
import app.otakureader.domain.model.ReadingDirection
import app.otakureader.domain.model.TapInvertMode
import app.otakureader.domain.model.TapZoneConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val dataStore: DataStore<Preferences>,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ReaderSettingsRepositoryInterface {
    /**
     * Emits an event whenever a DataStore write fails due to disk I/O.
     * Consumers can observe this to surface a user-facing warning.
     */
    private val _writeFailureEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val writeFailureEvents: Flow<Unit> = _writeFailureEvents.asSharedFlow()

    init {
        // Migrate the legacy image-quality ordinal key to the stable string key once on
        // startup, outside of any Flow transformation to avoid side effects in map().
        // Runs on the injected @ApplicationScope so the migration job is bounded by the
        // application lifecycle (no leaking ad-hoc scopes), and runs at most once per
        // process: a transient write failure will not retry within the same session — a
        // fresh app start will naturally retry because this is an in-memory init block.
        scope.launch {
            // Use safeEdit so I/O failures are surfaced via writeFailureEvents,
            // CancellationException is propagated for cooperative cancellation, and
            // any non-I/O runtime errors are not silently swallowed.
            safeEdit { prefs ->
                if (prefs[Keys.IMAGE_QUALITY] == null && prefs[Keys.IMAGE_QUALITY_LEGACY] != null) {
                    val ordinal = prefs[Keys.IMAGE_QUALITY_LEGACY] ?: return@safeEdit
                    val quality = ImageQuality.entries.getOrNull(ordinal) ?: ImageQuality.ORIGINAL
                    prefs[Keys.IMAGE_QUALITY] = quality.name
                    prefs.remove(Keys.IMAGE_QUALITY_LEGACY)
                }
            }
        }
    }

    // ==================== Reader Mode ====================
    
    override val readerMode: Flow<ReaderMode> = dataStore.data.map { prefs ->
        val ordinal = prefs[Keys.READER_MODE] ?: 0
        ReaderMode.entries.getOrNull(ordinal) ?: ReaderMode.SINGLE_PAGE
    }
    
    override suspend fun setReaderMode(mode: ReaderMode) {
        safeEdit { it[Keys.READER_MODE] = mode.ordinal }
    }

    // ==================== Orientation ====================

    override val readerOrientation: Flow<ReaderOrientation> = dataStore.data.map { prefs ->
        ReaderOrientation.fromOrdinal(prefs[Keys.READER_ORIENTATION])
    }

    override suspend fun setReaderOrientation(orientation: ReaderOrientation) {
        safeEdit { it[Keys.READER_ORIENTATION] = orientation.ordinal }
    }

    // ==================== Brightness ====================
    
    override val brightness: Flow<Float> = dataStore.data.map { prefs ->
        prefs[Keys.BRIGHTNESS] ?: DEFAULT_BRIGHTNESS
    }
    
    override suspend fun setBrightness(brightness: Float) {
        safeEdit { it[Keys.BRIGHTNESS] = brightness.coerceIn(0.1f, 1.5f) }
    }
    
    // ==================== Zoom Settings ====================
    
    val defaultZoom: Flow<Float> = dataStore.data.map { prefs ->
        prefs[Keys.ZOOM_LEVEL] ?: DEFAULT_ZOOM
    }
    
    suspend fun setDefaultZoom(zoom: Float) {
        safeEdit { it[Keys.ZOOM_LEVEL] = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM) }
    }
    
    // ==================== Double Tap Zoom ====================
    
    val doubleTapZoomEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DOUBLE_TAP_ZOOM] ?: true
    }
    
    override suspend fun setDoubleTapZoomEnabled(enabled: Boolean) {
        safeEdit { it[Keys.DOUBLE_TAP_ZOOM] = enabled }
    }
    
    // ==================== Keep Screen On ====================
    
    override val keepScreenOn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.KEEP_SCREEN_ON] ?: true
    }
    
    override suspend fun setKeepScreenOn(enabled: Boolean) {
        safeEdit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }
    
    // ==================== Show Page Number ====================
    
    override val showPageNumber: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_PAGE_NUMBER] ?: true
    }
    
    override suspend fun setShowPageNumber(enabled: Boolean) {
        safeEdit { it[Keys.SHOW_PAGE_NUMBER] = enabled }
    }
    
    // ==================== Reading Direction ====================
    
    override val readingDirection: Flow<ReadingDirection> = dataStore.data.map { prefs ->
        val ordinal = prefs[Keys.READING_DIRECTION] ?: 0
        ReadingDirection.entries.getOrNull(ordinal) ?: ReadingDirection.LTR
    }
    
    override suspend fun setReadingDirection(direction: ReadingDirection) {
        safeEdit { it[Keys.READING_DIRECTION] = direction.ordinal }
    }
    
    // ==================== Volume Key Navigation ====================
    
    override val volumeKeysEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.VOLUME_KEYS_ENABLED] ?: false
    }
    
    override suspend fun setVolumeKeysEnabled(enabled: Boolean) {
        safeEdit { it[Keys.VOLUME_KEYS_ENABLED] = enabled }
    }

    override val volumeKeysInverted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.VOLUME_KEYS_INVERTED] ?: false
    }

    override suspend fun setVolumeKeysInverted(inverted: Boolean) {
        safeEdit { it[Keys.VOLUME_KEYS_INVERTED] = inverted }
    }

    override val volumeKeyBehaviorSinglePage:  Flow<Int> = dataStore.data.map { it[Keys.VOL_KEY_SINGLE]  ?: 0 }
    override val volumeKeyBehaviorDualPage:    Flow<Int> = dataStore.data.map { it[Keys.VOL_KEY_DUAL]    ?: 0 }
    override val volumeKeyBehaviorWebtoon:     Flow<Int> = dataStore.data.map { it[Keys.VOL_KEY_WEBTOON] ?: 0 }
    override val volumeKeyBehaviorSmartPanels: Flow<Int> = dataStore.data.map { it[Keys.VOL_KEY_SMART]   ?: 0 }

    // ==================== Fullscreen ====================
    
    override val fullscreen: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.FULLSCREEN] ?: true
    }
    
    override suspend fun setFullscreen(enabled: Boolean) {
        safeEdit { it[Keys.FULLSCREEN] = enabled }
    }
    
    // ==================== Auto-Scroll Speed ====================
    
    override val autoScrollSpeed: Flow<Float> = dataStore.data.map { prefs ->
        prefs[Keys.AUTO_SCROLL_SPEED] ?: DEFAULT_AUTO_SCROLL_SPEED
    }
    
    override suspend fun setAutoScrollSpeed(speed: Float) {
        safeEdit { it[Keys.AUTO_SCROLL_SPEED] = speed.coerceIn(10f, 500f) }
    }
    
    // ==================== Tap Zone Configuration ====================

    override val tapZoneConfig: Flow<TapZoneConfig> = dataStore.data.map { prefs ->
        TapZoneConfig(
            leftZoneWidth = prefs[Keys.TAP_ZONE_LEFT] ?: 0.25f,
            centerZoneWidth = prefs[Keys.TAP_ZONE_CENTER] ?: 0.5f,
            rightZoneWidth = prefs[Keys.TAP_ZONE_RIGHT] ?: 0.25f
        )
    }

    override suspend fun setTapZoneConfig(config: TapZoneConfig) {
        safeEdit { prefs ->
            prefs[Keys.TAP_ZONE_LEFT] = config.leftZoneWidth
            prefs[Keys.TAP_ZONE_CENTER] = config.centerZoneWidth
            prefs[Keys.TAP_ZONE_RIGHT] = config.rightZoneWidth
        }
    }

    // ==================== Incognito Mode ====================

    override val incognitoMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.INCOGNITO_MODE] ?: false
    }

    override suspend fun setIncognitoMode(enabled: Boolean) {
        safeEdit { it[Keys.INCOGNITO_MODE] = enabled }
    }

    override val colorFilterMode: Flow<ColorFilterMode> = dataStore.data.map { prefs ->
        ColorFilterMode.entries.getOrNull(prefs[Keys.COLOR_FILTER_MODE] ?: 0)
            ?: ColorFilterMode.NONE
    }

    override suspend fun setColorFilterMode(mode: ColorFilterMode) {
        safeEdit { it[Keys.COLOR_FILTER_MODE] = mode.ordinal }
    }

    override val customTintColor: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_TINT_COLOR] ?: 0x4000AAFFL
    }

    override suspend fun setCustomTintColor(color: Long) {
        safeEdit { it[Keys.CUSTOM_TINT_COLOR] = color }
    }

    // ==================== Page Preloading (#264) ====================

    /** Number of pages to preload before the current page (0–10). */
    override val preloadPagesBefore: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.PRELOAD_PAGES_BEFORE] ?: DEFAULT_PRELOAD_PAGES
    }

    override suspend fun setPreloadPagesBefore(count: Int) {
        safeEdit { it[Keys.PRELOAD_PAGES_BEFORE] = count.coerceIn(0, MAX_PRELOAD_PAGES) }
    }

    /** Number of pages to preload after the current page (0–10). */
    override val preloadPagesAfter: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.PRELOAD_PAGES_AFTER] ?: DEFAULT_PRELOAD_PAGES
    }

    override suspend fun setPreloadPagesAfter(count: Int) {
        safeEdit { it[Keys.PRELOAD_PAGES_AFTER] = count.coerceIn(0, MAX_PRELOAD_PAGES) }
    }

    // ==================== Smart Prefetch Settings ====================

    /** Whether smart prefetch is enabled. */
    override val smartPrefetchEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SMART_PREFETCH_ENABLED] ?: true
    }

    suspend fun setSmartPrefetchEnabled(enabled: Boolean) {
        safeEdit { it[Keys.SMART_PREFETCH_ENABLED] = enabled }
    }

    /** Prefetch strategy ordinal (0=Conservative, 1=Balanced, 2=Aggressive, 3=Adaptive). */
    override val prefetchStrategyOrdinal: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.PREFETCH_STRATEGY] ?: 1 // Default to Balanced
    }

    suspend fun setPrefetchStrategy(strategyOrdinal: Int) {
        safeEdit { it[Keys.PREFETCH_STRATEGY] = strategyOrdinal.coerceIn(0, 3) }
    }

    /** Whether adaptive learning is enabled for prefetch optimization. */
    override val adaptiveLearningEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ADAPTIVE_LEARNING_ENABLED] ?: true
    }

    suspend fun setAdaptiveLearningEnabled(enabled: Boolean) {
        safeEdit { it[Keys.ADAPTIVE_LEARNING_ENABLED] = enabled }
    }

    /** Whether to prefetch adjacent chapters. */
    override val prefetchAdjacentChapters: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PREFETCH_ADJACENT_CHAPTERS] ?: false
    }

    suspend fun setPrefetchAdjacentChapters(enabled: Boolean) {
        safeEdit { it[Keys.PREFETCH_ADJACENT_CHAPTERS] = enabled }
    }

    /** Whether to only prefetch on WiFi (disable on mobile data). */
    override val prefetchOnlyOnWiFi: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PREFETCH_ONLY_ON_WIFI] ?: false
    }

    suspend fun setPrefetchOnlyOnWiFi(enabled: Boolean) {
        safeEdit { it[Keys.PREFETCH_ONLY_ON_WIFI] = enabled }
    }

    override val showPageThumbnailStrip: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_PAGE_THUMBNAIL_STRIP] ?: true
    }

    override suspend fun setShowPageThumbnailStrip(enabled: Boolean) {
        safeEdit { it[Keys.SHOW_PAGE_THUMBNAIL_STRIP] = enabled }
    }

    // ==================== Crop Borders ====================

    /** Whether automatic border cropping is enabled during image decoding. */
    override val cropBordersEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.CROP_BORDERS_ENABLED] ?: false
    }

    override suspend fun setCropBordersEnabled(enabled: Boolean) {
        safeEdit { it[Keys.CROP_BORDERS_ENABLED] = enabled }
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
    override val imageQuality: Flow<ImageQuality> = dataStore.data.map { prefs ->
        val name = prefs[Keys.IMAGE_QUALITY]
        if (name != null) {
            ImageQuality.entries.firstOrNull { it.name == name } ?: ImageQuality.ORIGINAL
        } else {
            // Migration runs in init; this branch handles reads before migration completes.
            val legacyOrdinal = prefs[Keys.IMAGE_QUALITY_LEGACY]
            ImageQuality.entries.getOrNull(legacyOrdinal ?: 0) ?: ImageQuality.ORIGINAL
        }
    }

    override suspend fun setImageQuality(quality: ImageQuality) {
        safeEdit { prefs ->
            prefs[Keys.IMAGE_QUALITY] = quality.name
            // Remove the legacy int key so future reads always take the new path.
            prefs.remove(Keys.IMAGE_QUALITY_LEGACY)
        }
    }

    // ==================== Data Saver Mode ====================

    /** Whether data saver mode is enabled to reduce image quality and bandwidth usage. */
    override val dataSaverEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DATA_SAVER_ENABLED] ?: false
    }

    override suspend fun setDataSaverEnabled(enabled: Boolean) {
        safeEdit { it[Keys.DATA_SAVER_ENABLED] = enabled }
    }

    // ==================== Overlay Settings ====================

    /** Whether the reading session timer overlay is shown in the reader. */
    override val showReadingTimer: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_READING_TIMER] ?: false
    }

    override suspend fun setShowReadingTimer(enabled: Boolean) {
        safeEdit { it[Keys.SHOW_READING_TIMER] = enabled }
    }

    /** Whether the battery/time overlay is shown in the reader. */
    override val showBatteryTime: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_BATTERY_TIME] ?: false
    }

    override suspend fun setShowBatteryTime(enabled: Boolean) {
        safeEdit { it[Keys.SHOW_BATTERY_TIME] = enabled }
    }

    // ==================== Display Settings ====================

    override val showContentInCutout: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_CONTENT_IN_CUTOUT] ?: true
    }

    override suspend fun setShowContentInCutout(enabled: Boolean) {
        safeEdit { it[Keys.SHOW_CONTENT_IN_CUTOUT] = enabled }
    }

    /** Background color: 0 = Black, 1 = White, 2 = Gray, 3 = Auto */
    override val backgroundColor: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.BACKGROUND_COLOR] ?: 0
    }

    override suspend fun setBackgroundColor(color: Int) {
        safeEdit { it[Keys.BACKGROUND_COLOR] = color.coerceIn(0, 3) }
    }

    override val animatePageTransitions: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ANIMATE_PAGE_TRANSITIONS] ?: true
    }

    override suspend fun setAnimatePageTransitions(enabled: Boolean) {
        safeEdit { it[Keys.ANIMATE_PAGE_TRANSITIONS] = enabled }
    }

    override val showReadingModeOverlay: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_READING_MODE_OVERLAY] ?: true
    }

    suspend fun setShowReadingModeOverlay(enabled: Boolean) {
        safeEdit { it[Keys.SHOW_READING_MODE_OVERLAY] = enabled }
    }

    override val showTapZonesOverlay: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_TAP_ZONES_OVERLAY] ?: false
    }

    suspend fun setShowTapZonesOverlay(enabled: Boolean) {
        safeEdit { it[Keys.SHOW_TAP_ZONES_OVERLAY] = enabled }
    }

    // ==================== Scale Settings ====================

    /** Scale type: 0 = Fit Screen, 1 = Fit Width, 2 = Fit Height, 3 = Original, 4 = Smart Fit */
    override val readerScale: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.READER_SCALE] ?: 0
    }

    override suspend fun setReaderScale(scale: Int) {
        safeEdit { it[Keys.READER_SCALE] = scale.coerceIn(0, 4) }
    }

    override val autoZoomWideImages: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.AUTO_ZOOM_WIDE_IMAGES] ?: true
    }

    override suspend fun setAutoZoomWideImages(enabled: Boolean) {
        safeEdit { it[Keys.AUTO_ZOOM_WIDE_IMAGES] = enabled }
    }

    // ==================== Tap Zone Settings ====================

    override val invertTapZones: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.INVERT_TAP_ZONES] ?: false
    }

    suspend fun setInvertTapZones(enabled: Boolean) {
        safeEdit { it[Keys.INVERT_TAP_ZONES] = enabled }
    }

    // ==================== Webtoon Settings ====================

    /** Side padding: 0 = None, 1 = Small, 2 = Medium, 3 = Large */
    override val webtoonSidePadding: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.WEBTOON_SIDE_PADDING] ?: 0
    }

    override suspend fun setWebtoonSidePadding(padding: Int) {
        safeEdit { it[Keys.WEBTOON_SIDE_PADDING] = padding.coerceIn(0, 3) }
    }

    /** Gap between webtoon pages in dp. Range: 0–16, default 4. */
    override val webtoonGapDp: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.WEBTOON_GAP_DP] ?: 4
    }

    suspend fun setWebtoonGapDp(gap: Int) {
        safeEdit { it[Keys.WEBTOON_GAP_DP] = gap.coerceIn(0, 16) }
    }

    /** Menu hide sensitivity: 0 = Low, 1 = Medium, 2 = High */
    override val webtoonMenuHideSensitivity: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.WEBTOON_MENU_HIDE_SENSITIVITY] ?: 0
    }

    suspend fun setWebtoonMenuHideSensitivity(sensitivity: Int) {
        safeEdit { it[Keys.WEBTOON_MENU_HIDE_SENSITIVITY] = sensitivity.coerceIn(0, 2) }
    }

    override val webtoonDoubleTapZoom: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.WEBTOON_DOUBLE_TAP_ZOOM] ?: true
    }

    override suspend fun setWebtoonDoubleTapZoom(enabled: Boolean) {
        safeEdit { it[Keys.WEBTOON_DOUBLE_TAP_ZOOM] = enabled }
    }

    override val webtoonDisableZoomOut: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.WEBTOON_DISABLE_ZOOM_OUT] ?: false
    }

    override suspend fun setWebtoonDisableZoomOut(enabled: Boolean) {
        safeEdit { it[Keys.WEBTOON_DISABLE_ZOOM_OUT] = enabled }
    }

    override val webtoonPinchToZoomEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.WEBTOON_PINCH_TO_ZOOM] ?: true
    }

    override suspend fun setWebtoonPinchToZoomEnabled(enabled: Boolean) {
        safeEdit { it[Keys.WEBTOON_PINCH_TO_ZOOM] = enabled }
    }

    /** 0=Fit Width, 1=Stretch, 2=Fit Page */
    override val webtoonScaleType: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.WEBTOON_SCALE_TYPE] ?: 0
    }

    override suspend fun setWebtoonScaleType(scaleType: Int) {
        safeEdit { it[Keys.WEBTOON_SCALE_TYPE] = scaleType.coerceIn(0, 2) }
    }

    // ==================== Pager display ====================

    /** 0=Fit Screen, 1=Zoom In (fill width in landscape) */
    override val landscapeZoomScaleType: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.LANDSCAPE_ZOOM_SCALE_TYPE] ?: 0
    }

    override suspend fun setLandscapeZoomScaleType(scaleType: Int) {
        safeEdit { it[Keys.LANDSCAPE_ZOOM_SCALE_TYPE] = scaleType.coerceIn(0, 1) }
    }

    /** 0=Single, 1=Double, 2=Automatic (dual when landscape) */
    override val pageLayout: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.PAGE_LAYOUT] ?: 0
    }

    override suspend fun setPageLayout(layout: Int) {
        safeEdit { it[Keys.PAGE_LAYOUT] = layout.coerceIn(0, 2) }
    }

    // ==================== Navigation Mode Settings ====================

    override val navigationModePager: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.NAV_MODE_PAGER] ?: 0
    }

    override suspend fun setNavigationModePager(mode: Int) {
        safeEdit { it[Keys.NAV_MODE_PAGER] = mode.coerceIn(0, 5) }
    }

    override val navigationModeWebtoon: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.NAV_MODE_WEBTOON] ?: 0
    }

    override suspend fun setNavigationModeWebtoon(mode: Int) {
        safeEdit { it[Keys.NAV_MODE_WEBTOON] = mode.coerceIn(0, 5) }
    }

    override val tapInvertModePager: Flow<TapInvertMode> = dataStore.data.map { prefs ->
        TapInvertMode.entries.getOrNull(prefs[Keys.TAP_INVERT_PAGER] ?: 0) ?: TapInvertMode.NONE
    }

    override suspend fun setTapInvertModePager(mode: TapInvertMode) {
        safeEdit { it[Keys.TAP_INVERT_PAGER] = mode.ordinal }
    }

    override val tapInvertModeWebtoon: Flow<TapInvertMode> = dataStore.data.map { prefs ->
        TapInvertMode.entries.getOrNull(prefs[Keys.TAP_INVERT_WEBTOON] ?: 0) ?: TapInvertMode.NONE
    }

    override suspend fun setTapInvertModeWebtoon(mode: TapInvertMode) {
        safeEdit { it[Keys.TAP_INVERT_WEBTOON] = mode.ordinal }
    }

    override val smallerTapZone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SMALLER_TAP_ZONE] ?: false
    }

    override suspend fun setSmallerTapZone(enabled: Boolean) {
        safeEdit { it[Keys.SMALLER_TAP_ZONE] = enabled }
    }

    // ==================== E-ink Settings ====================

    override val einkFlashOnPageChange: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.EINK_FLASH_ON_PAGE_CHANGE] ?: false
    }

    override suspend fun setEinkFlashOnPageChange(enabled: Boolean) {
        safeEdit { it[Keys.EINK_FLASH_ON_PAGE_CHANGE] = enabled }
    }

    override val einkBlackAndWhite: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.EINK_BLACK_AND_WHITE] ?: false
    }

    override suspend fun setEinkBlackAndWhite(enabled: Boolean) {
        safeEdit { it[Keys.EINK_BLACK_AND_WHITE] = enabled }
    }

    // ==================== Reading Behavior ====================

    override val skipReadChapters: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SKIP_READ_CHAPTERS] ?: false
    }

    override suspend fun setSkipReadChapters(enabled: Boolean) {
        safeEdit { it[Keys.SKIP_READ_CHAPTERS] = enabled }
    }

    override val skipFilteredChapters: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SKIP_FILTERED_CHAPTERS] ?: true
    }

    override suspend fun setSkipFilteredChapters(enabled: Boolean) {
        safeEdit { it[Keys.SKIP_FILTERED_CHAPTERS] = enabled }
    }

    override val skipDuplicateChapters: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SKIP_DUPLICATE_CHAPTERS] ?: false
    }

    override suspend fun setSkipDuplicateChapters(enabled: Boolean) {
        safeEdit { it[Keys.SKIP_DUPLICATE_CHAPTERS] = enabled }
    }

    override val alwaysShowChapterTransition: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ALWAYS_SHOW_CHAPTER_TRANSITION] ?: true
    }

    override suspend fun setAlwaysShowChapterTransition(enabled: Boolean) {
        safeEdit { it[Keys.ALWAYS_SHOW_CHAPTER_TRANSITION] = enabled }
    }

    // ==================== Actions ====================

    override val showActionsOnLongTap: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SHOW_ACTIONS_ON_LONG_TAP] ?: true
    }

    override suspend fun setShowActionsOnLongTap(enabled: Boolean) {
        safeEdit { it[Keys.SHOW_ACTIONS_ON_LONG_TAP] = enabled }
    }

    override val savePagesToSeparateFolders: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SAVE_PAGES_TO_SEPARATE_FOLDERS] ?: false
    }

    suspend fun setSavePagesToSeparateFolders(enabled: Boolean) {
        safeEdit { it[Keys.SAVE_PAGES_TO_SEPARATE_FOLDERS] = enabled }
    }

    override val secureScreen: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SECURE_SCREEN] ?: false
    }

    override suspend fun setSecureScreen(enabled: Boolean) {
        safeEdit { it[Keys.SECURE_SCREEN] = enabled }
    }


    /**
     * Wrapper around [DataStore.edit] that catches [java.io.IOException] so that a
     * transient disk write failure does not propagate as an unhandled exception and
     * crash the app (audit finding H-6).
     *
     * On failure the error is emitted via [writeFailureEvents] so the UI layer can
     * surface a user-visible warning. The in-memory preference value remains correct
     * for the current session even though the change was not persisted to disk.
     */
    private suspend inline fun safeEdit(crossinline block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { block(it) }
        } catch (_: java.io.IOException) {
            // Disk write failure — emit event so UI can show warning.
            _writeFailureEvents.tryEmit(Unit)
        }
    }

    private object Keys {
        val READER_MODE = intPreferencesKey("reader_mode_setting")
        val READER_ORIENTATION = intPreferencesKey("reader_orientation")
        val BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val ZOOM_LEVEL = floatPreferencesKey("reader_zoom_level")
        val DOUBLE_TAP_ZOOM = booleanPreferencesKey("reader_double_tap_zoom")
        val KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val SHOW_PAGE_NUMBER = booleanPreferencesKey("reader_show_page_number")
        val READING_DIRECTION = intPreferencesKey("reader_direction")
        val VOLUME_KEYS_ENABLED = booleanPreferencesKey("reader_volume_keys_enabled")
        val VOLUME_KEYS_INVERTED = booleanPreferencesKey("reader_volume_keys_inverted")
        val VOL_KEY_SINGLE  = intPreferencesKey("reader_vol_key_behavior_single")
        val VOL_KEY_DUAL    = intPreferencesKey("reader_vol_key_behavior_dual")
        val VOL_KEY_WEBTOON = intPreferencesKey("reader_vol_key_behavior_webtoon")
        val VOL_KEY_SMART   = intPreferencesKey("reader_vol_key_behavior_smart")
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
        
        // --- Display Settings ---
        val SHOW_CONTENT_IN_CUTOUT = booleanPreferencesKey("reader_show_content_in_cutout")
        val BACKGROUND_COLOR = intPreferencesKey("reader_background_color")
        val ANIMATE_PAGE_TRANSITIONS = booleanPreferencesKey("reader_animate_page_transitions")
        val SHOW_READING_MODE_OVERLAY = booleanPreferencesKey("reader_show_reading_mode_overlay")
        val SHOW_TAP_ZONES_OVERLAY = booleanPreferencesKey("reader_show_tap_zones_overlay")
        
        // --- Scale Settings ---
        val READER_SCALE = intPreferencesKey("reader_scale")
        val AUTO_ZOOM_WIDE_IMAGES = booleanPreferencesKey("reader_auto_zoom_wide_images")
        
        // --- Tap Zone Settings ---
        val INVERT_TAP_ZONES = booleanPreferencesKey("reader_invert_tap_zones")
        val NAV_MODE_PAGER = intPreferencesKey("reader_navigation_mode_pager")
        val NAV_MODE_WEBTOON = intPreferencesKey("reader_navigation_mode_webtoon")
        val TAP_INVERT_PAGER = intPreferencesKey("reader_tapping_inverted_pager")
        val TAP_INVERT_WEBTOON = intPreferencesKey("reader_tapping_inverted_webtoon")
        val SMALLER_TAP_ZONE = booleanPreferencesKey("reader_navigation_smaller_tap_zone")
        
        // --- Webtoon Settings ---
        val WEBTOON_SIDE_PADDING = intPreferencesKey("reader_webtoon_side_padding")
        val WEBTOON_GAP_DP = intPreferencesKey("reader_webtoon_gap_dp")
        val WEBTOON_MENU_HIDE_SENSITIVITY = intPreferencesKey("reader_webtoon_menu_hide_sensitivity")
        val WEBTOON_DOUBLE_TAP_ZOOM = booleanPreferencesKey("reader_webtoon_double_tap_zoom")
        val WEBTOON_DISABLE_ZOOM_OUT = booleanPreferencesKey("reader_webtoon_disable_zoom_out")
        val WEBTOON_PINCH_TO_ZOOM = booleanPreferencesKey("reader_webtoon_pinch_to_zoom")
        val WEBTOON_SCALE_TYPE = intPreferencesKey("reader_webtoon_scale_type")
        val LANDSCAPE_ZOOM_SCALE_TYPE = intPreferencesKey("reader_landscape_zoom_scale_type")
        val PAGE_LAYOUT = intPreferencesKey("reader_page_layout")
        
        // --- E-ink Settings ---
        val EINK_FLASH_ON_PAGE_CHANGE = booleanPreferencesKey("reader_eink_flash_on_page_change")
        val EINK_BLACK_AND_WHITE = booleanPreferencesKey("reader_eink_black_and_white")
        
        // --- Reading Behavior ---
        val SKIP_READ_CHAPTERS = booleanPreferencesKey("reader_skip_read_chapters")
        val SKIP_FILTERED_CHAPTERS = booleanPreferencesKey("reader_skip_filtered_chapters")
        val SKIP_DUPLICATE_CHAPTERS = booleanPreferencesKey("reader_skip_duplicate_chapters")
        val ALWAYS_SHOW_CHAPTER_TRANSITION = booleanPreferencesKey("reader_always_show_chapter_transition")
        
        // --- Actions ---
        val SHOW_ACTIONS_ON_LONG_TAP = booleanPreferencesKey("reader_show_actions_on_long_tap")
        val SAVE_PAGES_TO_SEPARATE_FOLDERS = booleanPreferencesKey("reader_save_pages_to_separate_folders")
        val SHOW_PAGE_THUMBNAIL_STRIP = booleanPreferencesKey("reader_show_page_thumbnail_strip")
        val SECURE_SCREEN = booleanPreferencesKey("reader_secure_screen")
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
