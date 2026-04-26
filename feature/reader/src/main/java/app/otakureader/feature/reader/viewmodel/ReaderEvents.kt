package app.otakureader.feature.reader.viewmodel

import app.otakureader.domain.model.ColorFilterMode
import app.otakureader.domain.model.ReaderMode
import app.otakureader.domain.model.ReadingDirection
import app.otakureader.domain.model.TapZoneConfig

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

    /** Events that change the color filter or tint applied to pages. */
    sealed interface ColorFilterControl : ReaderEvent

    /** Events related to SFX translation. */
    sealed interface SfxControl : ReaderEvent

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
