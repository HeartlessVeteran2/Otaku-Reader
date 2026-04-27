package app.otakureader.feature.reader

import app.otakureader.domain.model.ColorFilterMode
import app.otakureader.domain.model.ImageQuality
import app.otakureader.domain.model.ReaderMode
import app.otakureader.domain.model.ReadingDirection
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.domain.model.TapZoneConfig

/**
 * Page rotation applied to all pages in the current reading session.
 * Each step represents a 90° clockwise rotation.
 */
enum class PageRotation(val degrees: Float) {
    NONE(0f),
    CW_90(90f),
    CW_180(180f),
    CW_270(270f);

    /** Returns the next clockwise rotation (wraps around). */
    fun next(): PageRotation = entries[(ordinal + 1) % entries.size]
}

/**
 * Represents the complete state of the reader at any given moment.
 * This is a single source of truth for all UI components.
 */
data class ReaderState(
    /** List of all pages in the current chapter */
    val pages: List<ReaderPage> = emptyList(),

    /** Current page index (0-based) */
    val currentPage: Int = 0,

    /** Current panel index for smart panel mode (0-based) */
    val currentPanel: Int = 0,

    /** Current reader display mode */
    val mode: ReaderMode = ReaderMode.SINGLE_PAGE,

    /** Current zoom level (1.0 = 100%, 2.0 = 200%, etc.) */
    val zoomLevel: Float = 1f,

    /** Whether the reader menu/controls are visible */
    val isMenuVisible: Boolean = false,

    /** Whether the page gallery/thumbnail view is open */
    val isGalleryOpen: Boolean = false,

    /** Number of columns displayed in the gallery grid (2, 3, or 4) */
    val galleryColumns: Int = 3,

    /** Screen brightness level (0.1 - 1.5) */
    val brightness: Float = 1f,

    /** Loading state for chapter loading */
    val isLoading: Boolean = false,

    /** Error message if something went wrong */
    val error: String? = null,

    /** Whether auto-scroll is enabled (webtoon mode) */
    val isAutoScrollEnabled: Boolean = false,

    /** Auto-scroll speed in pixels per second */
    val autoScrollSpeed: Float = 100f,

    /** Whether the reader is in fullscreen mode */
    val isFullscreen: Boolean = true,

    /** Current reading direction (for RTL manga) */
    val readingDirection: ReadingDirection = ReadingDirection.LTR,

    /** Whether double-tap to zoom is enabled */
    val doubleTapZoomEnabled: Boolean = true,

    /** Whether volume keys can be used for navigation */
    val volumeKeysEnabled: Boolean = false,

    /** Whether volume key directions are inverted (up=next) */
    val volumeKeysInverted: Boolean = false,

    /** Keep screen on while reading */
    val keepScreenOn: Boolean = true,

    /** Show page number indicator */
    val showPageNumber: Boolean = true,

    /** Current chapter title */
    val chapterTitle: String = "",

    /** Incognito mode - when enabled, reading history is not saved */
    val incognitoMode: Boolean = false,

    /** Color filter mode applied over the page content */
    val colorFilterMode: ColorFilterMode = ColorFilterMode.NONE,

    /** Custom tint color used when colorFilterMode == CUSTOM_TINT (ARGB color 0xAARRGGBB stored in a Long) */
    val customTintColor: Long = 0x4000AAFFL,

    /** Per-manga reader background color (ARGB Long), or null for default (black). */
    val readerBackgroundColor: Long? = null,

    /** Current page rotation applied to all pages in the session */
    val pageRotation: PageRotation = PageRotation.NONE,

    /** Show reading timer overlay (displays session duration) */
    val showReadingTimer: Boolean = false,

    /** Show battery/time overlay (displays battery level and system time) */
    val showBatteryTime: Boolean = false,

    /** Whether automatic border cropping is enabled for page images */
    val cropBordersEnabled: Boolean = false,

    /** Image quality level for page rendering (controls decode size and filter quality). */
    val imageQuality: ImageQuality = ImageQuality.ORIGINAL,

    /** Whether data saver mode is enabled to reduce image quality and bandwidth usage */
    val dataSaverEnabled: Boolean = false,

    // --- Display Settings ---
    /** Show content in display cutout/notch area */
    val showContentInCutout: Boolean = true,
    /** Reader background color: 0 = Black, 1 = White, 2 = Gray, 3 = Auto */
    val backgroundColor: Int = 0,
    /** Animate page transitions */
    val animatePageTransitions: Boolean = true,
    /** Show reading mode overlay briefly when switching */
    val showReadingModeOverlay: Boolean = true,
    /** Show tap zones overlay for learning */
    val showTapZonesOverlay: Boolean = false,

    // --- Scale Settings ---
    /** Scale type: 0 = Fit Screen, 1 = Fit Width, 2 = Fit Height, 3 = Original, 4 = Smart Fit */
    val readerScale: Int = 0,
    /** Auto-zoom wide images (double-page spreads) */
    val autoZoomWideImages: Boolean = true,

    // --- Tap Zone Settings ---
    /** Invert tap zone actions (swap prev/next) */
    val invertTapZones: Boolean = false,

    // --- Webtoon Settings ---
    /** Side padding for webtoon: 0 = None, 1 = Small, 2 = Medium, 3 = Large */
    val webtoonSidePadding: Int = 0,
    /** Gap between webtoon pages in dp (0–16, default 4) */
    val webtoonGapDp: Int = 4,
    /** Menu hide sensitivity: 0 = Low, 1 = Medium, 2 = High */
    val webtoonMenuHideSensitivity: Int = 0,
    /** Enable double-tap zoom in webtoon mode */
    val webtoonDoubleTapZoom: Boolean = true,
    /** Disable zooming out past fit-width in webtoon */
    val webtoonDisableZoomOut: Boolean = false,

    // --- E-ink Settings ---
    /** Flash screen on page change for E-ink displays */
    val einkFlashOnPageChange: Boolean = false,
    /** Black and white mode for E-ink displays */
    val einkBlackAndWhite: Boolean = false,

    // --- Reading Behavior ---
    /** Skip chapters already marked as read when navigating */
    val skipReadChapters: Boolean = false,
    /** Skip chapters hidden by filters */
    val skipFilteredChapters: Boolean = true,
    /** Skip duplicate chapter names */
    val skipDuplicateChapters: Boolean = false,
    /** Always show chapter transition info */
    val alwaysShowChapterTransition: Boolean = true,

    // --- Actions ---
    /** Show actions menu on long tap */
    val showActionsOnLongTap: Boolean = true,
    /** Save pages to separate folders by manga title */
    val savePagesToSeparateFolders: Boolean = false,

    // --- SFX Translation ---
    /** Whether the SFX translation feature is enabled in settings. */
    val sfxTranslationEnabled: Boolean = false,
    /** AI-generated SFX translations keyed by zero-based page index. */
    val sfxTranslations: Map<Int, List<app.otakureader.domain.model.SfxTranslation>> = emptyMap(),
    /** True while an SFX translation request is in progress. */
    val isSfxTranslating: Boolean = false,
    /** Whether the SFX translation dialog is visible. */
    val showSfxDialog: Boolean = false,
    /** Whether the SFX translation overlay is currently visible. */
    val sfxOverlayVisible: Boolean = false,

    // --- OCR Text Search ---
    /** Whether the OCR text search bottom sheet is visible. */
    val showOcrSearch: Boolean = false,
    /** Current search query entered by the user. */
    val ocrQuery: String = "",
    /** OCR-extracted text keyed by zero-based page index (populated lazily as pages are scanned). */
    val ocrPageTexts: Map<Int, String> = emptyMap(),
    /** True while background OCR scanning is in progress. */
    val isOcrRunning: Boolean = false,

    // --- OCR Translation (Gemini Vision) ---
    /** Whether the Gemini Vision OCR translation feature is enabled in settings. */
    val ocrTranslationEnabled: Boolean = false,
    /** AI-generated OCR translations keyed by zero-based page index. */
    val ocrTranslations: Map<Int, List<app.otakureader.domain.model.OcrTranslation>> = emptyMap(),
    /** True while a Gemini Vision translation request is in progress for any page. */
    val isOcrTranslating: Boolean = false,
    /** Whether the OCR translation results sheet is visible. */
    val showOcrTranslationSheet: Boolean = false,
) {
    /** Total pages in chapter (derived from pages.size) */
    val totalPages: Int get() = pages.size
    /** Computed property for progress percentage */
    val progressPercent: Float
        get() = if (pages.isNotEmpty()) {
            ((currentPage + 1).toFloat() / pages.size.toFloat()) * 100f
        } else 0f

    /** Check if we're on the first page */
    val isFirstPage: Boolean
        get() = currentPage <= 0

    /** Check if we're on the last page */
    val isLastPage: Boolean
        get() = currentPage >= pages.size - 1

    /** Get current page or null */
    val currentPageData: ReaderPage?
        get() = pages.getOrNull(currentPage)

    /** Get display page number (1-based) */
    val displayPageNumber: Int
        get() = currentPage + 1

    /** Check if dual page mode should show spread */
    val isDualPageSpread: Boolean
        get() = mode == ReaderMode.DUAL_PAGE && currentPage % 2 == 0

    /** Get companion page for dual page mode */
    val companionPage: ReaderPage?
        get() = if (mode == ReaderMode.DUAL_PAGE && currentPage + 1 < pages.size) {
            pages[currentPage + 1]
        } else null

    // ── Sub-state views ───────────────────────────────────────────────────────
    // These are lightweight projections of the monolithic state. They allow
    // composables and future ViewModels to observe only the slice they care about,
    // and serve as the intended decomposition targets when the God ViewModel is
    // split into domain-specific ViewModels (see issue #581).

    /** Page content and navigation state — changes on every page turn. */
    val pageState: PageState
        get() = PageState(
            pages = pages,
            currentPage = currentPage,
            currentPanel = currentPanel,
            mode = mode,
            readingDirection = readingDirection,
            isLoading = isLoading,
            error = error,
            chapterTitle = chapterTitle
        )

    /** Overlay and menu visibility state — changes on tap/gesture. */
    val overlayState: OverlayState
        get() = OverlayState(
            isMenuVisible = isMenuVisible,
            isGalleryOpen = isGalleryOpen,
            galleryColumns = galleryColumns,
            isFullscreen = isFullscreen,
            showTapZonesOverlay = showTapZonesOverlay
        )

    /** Display and rendering settings — changes infrequently. */
    val displayState: DisplayState
        get() = DisplayState(
            zoomLevel = zoomLevel,
            pageRotation = pageRotation,
            brightness = brightness,
            colorFilterMode = colorFilterMode,
            customTintColor = customTintColor,
            readerBackgroundColor = readerBackgroundColor,
            cropBordersEnabled = cropBordersEnabled,
            imageQuality = imageQuality,
            dataSaverEnabled = dataSaverEnabled,
            readerScale = readerScale,
            animatePageTransitions = animatePageTransitions,
            showReadingTimer = showReadingTimer,
            showBatteryTime = showBatteryTime,
            showPageNumber = showPageNumber,
            einkFlashOnPageChange = einkFlashOnPageChange,
            einkBlackAndWhite = einkBlackAndWhite
        )

    /** SFX translation state — changes when translation is requested or dialog toggled. */
    val sfxState: SfxState
        get() = SfxState(
            sfxTranslationEnabled = sfxTranslationEnabled,
            sfxTranslations = sfxTranslations,
            isSfxTranslating = isSfxTranslating,
            showSfxDialog = showSfxDialog,
            sfxOverlayVisible = sfxOverlayVisible
        )

    /** Webtoon-specific state — only relevant in webtoon mode. */
    val webtoonState: WebtoonState
        get() = WebtoonState(
            isAutoScrollEnabled = isAutoScrollEnabled,
            autoScrollSpeed = autoScrollSpeed,
            webtoonSidePadding = webtoonSidePadding,
            webtoonGapDp = webtoonGapDp,
            webtoonMenuHideSensitivity = webtoonMenuHideSensitivity,
            webtoonDoubleTapZoom = webtoonDoubleTapZoom,
            webtoonDisableZoomOut = webtoonDisableZoomOut
        )

    /** OCR text-search state — changes when the search sheet is opened/closed or results arrive. */
    val ocrState: OcrState
        get() = OcrState(
            showOcrSearch = showOcrSearch,
            ocrQuery = ocrQuery,
            ocrPageTexts = ocrPageTexts,
            isOcrRunning = isOcrRunning,
        )

    /**
     * Pages (by 0-based index) whose recognized text contains [ocrQuery] (case-insensitive).
     * Empty when the query is blank or no pages have been indexed yet.
     *
     * Computed once per immutable [ReaderState] instance so Compose reads do not
     * repeatedly scan and sort the full OCR text map during recomposition.
     */
    val ocrMatchingPageIndices: List<Int> by lazy {
        computeOcrMatchingPageIndices()
    }

    private fun computeOcrMatchingPageIndices(): List<Int> {
        val q = ocrQuery.trim()
        if (q.isBlank()) return emptyList()
        return ocrPageTexts
            .asSequence()
            .filter { (_, text) -> text.contains(q, ignoreCase = true) }
            .map { (pageIndex, _) -> pageIndex }
            .sorted()
            .toList()
    }
}

/** Projection of page content and navigation fields. */
data class PageState(
    val pages: List<ReaderPage>,
    val currentPage: Int,
    val currentPanel: Int,
    val mode: ReaderMode,
    val readingDirection: ReadingDirection,
    val isLoading: Boolean,
    val error: String?,
    val chapterTitle: String
)

/** Projection of overlay and menu visibility fields. */
data class OverlayState(
    val isMenuVisible: Boolean,
    val isGalleryOpen: Boolean,
    val galleryColumns: Int,
    val isFullscreen: Boolean,
    val showTapZonesOverlay: Boolean
)

/** Projection of rendering and display preference fields. */
data class DisplayState(
    val zoomLevel: Float,
    val pageRotation: PageRotation,
    val brightness: Float,
    val colorFilterMode: ColorFilterMode,
    val customTintColor: Long,
    val readerBackgroundColor: Long?,
    val cropBordersEnabled: Boolean,
    val imageQuality: ImageQuality,
    val dataSaverEnabled: Boolean,
    val readerScale: Int,
    val animatePageTransitions: Boolean,
    val showReadingTimer: Boolean,
    val showBatteryTime: Boolean,
    val showPageNumber: Boolean,
    val einkFlashOnPageChange: Boolean,
    val einkBlackAndWhite: Boolean
)

/** Projection of SFX translation state. */
data class SfxState(
    val sfxTranslationEnabled: Boolean,
    val sfxTranslations: Map<Int, List<app.otakureader.domain.model.SfxTranslation>>,
    val isSfxTranslating: Boolean,
    val showSfxDialog: Boolean,
    val sfxOverlayVisible: Boolean
)

/** Projection of webtoon-mode-specific settings. */
data class WebtoonState(
    val isAutoScrollEnabled: Boolean,
    val autoScrollSpeed: Float,
    val webtoonSidePadding: Int,
    val webtoonGapDp: Int,
    val webtoonMenuHideSensitivity: Int,
    val webtoonDoubleTapZoom: Boolean,
    val webtoonDisableZoomOut: Boolean
)

/** Projection of OCR text-search state. */
data class OcrState(
    val showOcrSearch: Boolean,
    val ocrQuery: String,
    val ocrPageTexts: Map<Int, String>,
    val isOcrRunning: Boolean,
)

/**
 * Speed settings for smart panel navigation
 */
enum class SmartPanelSpeed {
    SLOW,    // Careful, detailed navigation
    NORMAL,  // Balanced speed
    FAST     // Quick panel transitions
}

/**
 * Sealed interface representing all possible user interactions in the reader.
 * Events are grouped into domain-specific sealed sub-interfaces so that the
 * ViewModel can handle an entire domain with a single `is` check, while the
 * individual leaf types remain accessible at the top level (e.g.
 * `ReaderEvent.NextPage`) so existing callers are unaffected.
 */
sealed interface ReaderEvent {

    // ──────────────────────────────────────────────────────────────────────────
    // Domain markers — sealed so Kotlin knows all implementations at compile time
    // ──────────────────────────────────────────────────────────────────────────

    /** Events that directly navigate to a page, panel, or position. */
    sealed interface Navigation : ReaderEvent

    /** Events that move sequentially through pages. */
    sealed interface PageNavigation : Navigation

    /** Events that move through panels in Smart Panels mode. */
    sealed interface PanelNavigation : Navigation

    /** Events that change the reader chapter. */
    sealed interface ChapterNavigation : Navigation

    /** Events that control zoom level. */
    sealed interface ZoomControl : ReaderEvent

    /** Events that change global reader display/settings. */
    sealed interface DisplayControl : ReaderEvent

    /** Events that manage the overlay UI (menus, gallery, fullscreen). */
    sealed interface OverlayControl : ReaderEvent

    /** Events related to screen brightness adjustment. */
    sealed interface BrightnessControl : ReaderEvent

    /** Events related to auto-scroll. */
    sealed interface AutoScrollControl : ReaderEvent

    /** Events that change persistent reader settings. */
    sealed interface SettingsControl : ReaderEvent

    /** Events related to the color filter or tint applied to pages. */
    sealed interface ColorFilterControl : ReaderEvent

    /** Events related to SFX translation. */
    sealed interface SfxControl : ReaderEvent

    /** Events related to OCR text search within chapter pages. */
    sealed interface OcrControl : ReaderEvent

    /** Events related to Gemini Vision OCR translation. */
    sealed interface OcrTranslationControl : ReaderEvent

    /** Miscellaneous action events that don't fit another domain. */
    sealed interface ActionEvent : ReaderEvent

    // ──────────────────────────────────────────────────────────────────────────
    // Navigation — page position changes
    // ──────────────────────────────────────────────────────────────────────────

    /** Navigate to a specific page by index (0-based). */
    data class OnPageChange(val page: Int) : Navigation

    /** Navigate to a specific panel within the current page (smart panel mode). */
    data class OnPanelChange(val panel: Int) : PanelNavigation

    /** Change the reader display mode (single, dual, webtoon, smart panels). */
    data class OnModeChange(val mode: ReaderMode) : DisplayControl

    /** Change reading direction (LTR/RTL/Vertical). */
    data class OnDirectionChange(val direction: ReadingDirection) : DisplayControl

    /** Navigate to next page (respects reading direction). */
    data object NextPage : PageNavigation

    /** Navigate to previous page (respects reading direction). */
    data object PrevPage : PageNavigation

    /** Navigate to first page of chapter. */
    data object FirstPage : PageNavigation

    /** Navigate to last page of chapter. */
    data object LastPage : PageNavigation

    /** Navigate to next panel (smart panel mode). */
    data object NextPanel : PanelNavigation

    /** Navigate to previous panel (smart panel mode). */
    data object PrevPanel : PanelNavigation

    /** Navigate to first panel on current page. */
    data object FirstPanel : PanelNavigation

    /** Navigate to last panel on current page. */
    data object LastPanel : PanelNavigation

    /** Load a new chapter; resume from [resumeFromPage] (default 0). */
    data class LoadChapter(val chapterId: String, val resumeFromPage: Int = 0) : ChapterNavigation

    /** Navigate to next chapter. */
    data object NextChapter : ChapterNavigation

    /** Navigate to previous chapter. */
    data object PrevChapter : ChapterNavigation

    // ──────────────────────────────────────────────────────────────────────────
    // Zoom
    // ──────────────────────────────────────────────────────────────────────────

    /** Change the zoom level to an explicit multiplier (1.0 = 100%). */
    data class OnZoomChange(val zoom: Float) : ZoomControl

    /** Zoom in by a fixed increment. */
    data object ZoomIn : ZoomControl

    /** Zoom out by a fixed increment. */
    data object ZoomOut : ZoomControl

    /** Reset zoom to 100%. */
    data object ResetZoom : ZoomControl

    /** Zoom to fit page width. */
    data object ZoomToWidth : ZoomControl

    /** Zoom to fit page height. */
    data object ZoomToHeight : ZoomControl

    // ──────────────────────────────────────────────────────────────────────────
    // Overlay / UI controls
    // ──────────────────────────────────────────────────────────────────────────

    /** Toggle the visibility of the reader menu/controls. */
    data object ToggleMenu : OverlayControl

    /** Toggle the page gallery/thumbnail view. */
    data object ToggleGallery : OverlayControl

    /** Set the number of columns in the gallery grid (2, 3, or 4). */
    data class SetGalleryColumns(val columns: Int) : OverlayControl

    /** Toggle fullscreen mode. */
    data object ToggleFullscreen : OverlayControl

    /** Toggle auto-scroll (webtoon mode). */
    data object ToggleAutoScroll : AutoScrollControl

    // ──────────────────────────────────────────────────────────────────────────
    // Brightness
    // ──────────────────────────────────────────────────────────────────────────

    /** Set screen brightness (0.1 – 1.5). */
    data class OnBrightnessChange(val brightness: Float) : BrightnessControl

    /** Increase brightness by one increment. */
    data object BrightnessUp : BrightnessControl

    /** Decrease brightness by one increment. */
    data object BrightnessDown : BrightnessControl

    // ──────────────────────────────────────────────────────────────────────────
    // Auto-scroll
    // ──────────────────────────────────────────────────────────────────────────

    /** Set auto-scroll speed in pixels per second. */
    data class OnAutoScrollSpeedChange(val speed: Float) : AutoScrollControl

    /** Increase auto-scroll speed by one increment. */
    data object AutoScrollSpeedUp : AutoScrollControl

    /** Decrease auto-scroll speed by one increment. */
    data object AutoScrollSpeedDown : AutoScrollControl

    // ──────────────────────────────────────────────────────────────────────────
    // Settings
    // ──────────────────────────────────────────────────────────────────────────

    /** Toggle a boolean reader setting. */
    data class ToggleSetting(val setting: ReaderSetting) : SettingsControl

    /** Update tap zone configuration. */
    data class UpdateTapZones(val config: TapZoneConfig) : SettingsControl

    // ──────────────────────────────────────────────────────────────────────────
    // Color filter
    // ──────────────────────────────────────────────────────────────────────────

    /** Set the color filter overlay mode. */
    data class SetColorFilterMode(val mode: ColorFilterMode) : ColorFilterControl

    /** Set the custom tint color (ARGB Long) used when mode is [ColorFilterMode.CUSTOM_TINT]. */
    data class SetCustomTintColor(val color: Long) : ColorFilterControl

    /** Set the per-manga reader background color (ARGB Long), or null for default. */
    data class SetReaderBackgroundColor(val color: Long?) : ColorFilterControl

    // ──────────────────────────────────────────────────────────────────────────
    // Rotation
    // ──────────────────────────────────────────────────────────────────────────

    /** Rotate all pages 90° clockwise (cycles 0°→90°→180°→270°→0°). */
    data object RotateCW : DisplayControl

    /** Reset page rotation to 0°. */
    data object ResetRotation : DisplayControl

    // ──────────────────────────────────────────────────────────────────────────
    // SFX translation
    // ──────────────────────────────────────────────────────────────────────────

    /** Open the SFX translation dialog. */
    data object OpenSfxDialog : SfxControl

    /** Dismiss the SFX translation dialog. */
    data object CloseSfxDialog : SfxControl

    /** Request a translation for a sound effect text (e.g. "ドカン"). */
    data class TranslateSfx(val sfxText: String) : SfxControl

    // ──────────────────────────────────────────────────────────────────────────
    // Action events
    // ──────────────────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────────────────
    // OCR text search
    // ──────────────────────────────────────────────────────────────────────────

    /** Open the OCR text search bottom sheet and start background OCR scanning. */
    data object OpenOcrSearch : OcrControl

    /** Close the OCR text search bottom sheet and cancel background OCR jobs. */
    data object CloseOcrSearch : OcrControl

    /** Update the search query and filter live results. */
    data class UpdateOcrQuery(val query: String) : OcrControl

    // ──────────────────────────────────────────────────────────────────────────
    // OCR translation (Gemini Vision) — on-demand per page only
    // ──────────────────────────────────────────────────────────────────────────

    /** Translate the current page using Gemini Vision (single-shot, on demand). */
    data object TranslateCurrentPage : OcrTranslationControl

    /** Open the bottom sheet displaying OCR translations for the current page. */
    data object OpenOcrTranslationSheet : OcrTranslationControl

    /** Dismiss the OCR translation results sheet. */
    data object CloseOcrTranslationSheet : OcrTranslationControl

    // ──────────────────────────────────────────────────────────────────────────
    // Action events (existing)
    // ──────────────────────────────────────────────────────────────────────────

    /** Toggle bookmark on current page. */
    data object ToggleBookmark : ActionEvent

    /** Share the current page image. */
    data object SharePage : ActionEvent

    /** Dismiss the current error message. */
    data object DismissError : ActionEvent

    /** Retry the last failed operation. */
    data object Retry : ActionEvent

    // ──────────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────────

    companion object {
        const val ZOOM_INCREMENT = 0.25f
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 5.0f
        const val BRIGHTNESS_INCREMENT = 0.1f
        const val AUTO_SCROLL_INCREMENT = 50f
    }
}

/**
 * Identifiers for toggleable reader settings
 */
enum class ReaderSetting {
    KEEP_SCREEN_ON,
    SHOW_PAGE_NUMBER,
    DOUBLE_TAP_ZOOM,
    VOLUME_KEY_NAVIGATION,
    VOLUME_KEYS_INVERTED,
    INCOGNITO_MODE,
    CROP_BORDERS,
    SHOW_CLOCK,
    SHOW_BATTERY,
    INVERT_COLORS,
    GRAYSCALE,
    SEPIA_MODE
}

/**
 * Tap zones for navigation
 */
enum class TapZone {
    LEFT, CENTER, RIGHT
}

/**
 * Extension functions for creating common event sequences
 */
object ReaderEventExtensions {

    /** Create a jump to page event with bounds checking */
    fun jumpToPage(page: Int, totalPages: Int): ReaderEvent.OnPageChange {
        return ReaderEvent.OnPageChange(page.coerceIn(0, totalPages - 1))
    }

    /** Create page change events respecting reading direction */
    fun nextPage(direction: ReadingDirection): ReaderEvent {
        return when (direction) {
            ReadingDirection.LTR -> ReaderEvent.NextPage
            ReadingDirection.RTL -> ReaderEvent.PrevPage
            ReadingDirection.VERTICAL -> ReaderEvent.NextPage
        }
    }

    fun prevPage(direction: ReadingDirection): ReaderEvent {
        return when (direction) {
            ReadingDirection.LTR -> ReaderEvent.PrevPage
            ReadingDirection.RTL -> ReaderEvent.NextPage
            ReadingDirection.VERTICAL -> ReaderEvent.PrevPage
        }
    }
}

/**
 * Sealed interface representing side effects that should trigger UI responses.
 */
sealed interface ReaderEffect {
    data object NavigateBack : ReaderEffect
    data class ShowSnackbar(
        val message: String? = null,
        @androidx.annotation.StringRes val messageResId: Int? = null
    ) : ReaderEffect
    data class NavigateToChapter(val chapterId: Long) : ReaderEffect
}
