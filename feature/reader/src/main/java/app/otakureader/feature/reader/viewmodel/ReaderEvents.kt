package app.otakureader.feature.reader.viewmodel

import app.otakureader.feature.reader.model.ColorFilterMode
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.model.TapZoneConfig

/**
 * Sealed class representing all possible user interactions in the reader.
 * These events are dispatched from the UI and handled by the ViewModel.
 */
sealed interface ReaderEvent {

    // ==================== Navigation Events ====================

    /**
     * Navigate to a specific page by index
     * @param page Zero-based page index
     */
    data class OnPageChange(val page: Int) : ReaderEvent

    /**
     * Navigate to a specific panel within the current page (smart panel mode)
     * @param panel Zero-based panel index
     */
    data class OnPanelChange(val panel: Int) : ReaderEvent

    /**
     * Change the zoom level
     * @param zoom Zoom multiplier (1.0 = 100%, 2.0 = 200%)
     */
    data class OnZoomChange(val zoom: Float) : ReaderEvent

    /**
     * Change the reader display mode
     * @param mode New reader mode (single, dual, webtoon, smart panels)
     */
    data class OnModeChange(val mode: ReaderMode) : ReaderEvent

    /**
     * Change reading direction (LTR/RTL)
     * @param direction New reading direction
     */
    data class OnDirectionChange(val direction: ReadingDirection) : ReaderEvent

    // ==================== UI Toggle Events ====================

    /** Toggle the visibility of the reader menu/controls */
    data object ToggleMenu : ReaderEvent

    /** Toggle the page gallery/thumbnail view */
    data object ToggleGallery : ReaderEvent

    /**
     * Set the number of columns in the gallery grid
     * @param columns Number of columns (2, 3, or 4)
     */
    data class SetGalleryColumns(val columns: Int) : ReaderEvent

    /** Toggle fullscreen mode */
    data object ToggleFullscreen : ReaderEvent

    /** Toggle auto-scroll (webtoon mode) */
    data object ToggleAutoScroll : ReaderEvent

    // ==================== Page Navigation Events ====================

    /** Navigate to next page (respects reading direction) */
    data object NextPage : ReaderEvent

    /** Navigate to previous page (respects reading direction) */
    data object PrevPage : ReaderEvent

    /** Navigate to first page of chapter */
    data object FirstPage : ReaderEvent

    /** Navigate to last page of chapter */
    data object LastPage : ReaderEvent

    // ==================== Panel Navigation Events ====================

    /** Navigate to next panel (smart panel mode) */
    data object NextPanel : ReaderEvent

    /** Navigate to previous panel (smart panel mode) */
    data object PrevPanel : ReaderEvent

    /** Navigate to first panel on current page */
    data object FirstPanel : ReaderEvent

    /** Navigate to last panel on current page */
    data object LastPanel : ReaderEvent

    // ==================== Zoom Events ====================

    /** Zoom in by a fixed increment */
    data object ZoomIn : ReaderEvent

    /** Zoom out by a fixed increment */
    data object ZoomOut : ReaderEvent

    /** Reset zoom to 100% */
    data object ResetZoom : ReaderEvent

    /** Zoom to fit page width */
    data object ZoomToWidth : ReaderEvent

    /** Zoom to fit page height */
    data object ZoomToHeight : ReaderEvent

    // ==================== Chapter Events ====================

    /**
     * Load a new chapter
     * @param chapterId Chapter identifier
     * @param resumeFromPage Page to resume from (default: 0)
     */
    data class LoadChapter(val chapterId: String, val resumeFromPage: Int = 0) : ReaderEvent

    /** Navigate to next chapter */
    data object NextChapter : ReaderEvent

    /** Navigate to previous chapter */
    data object PrevChapter : ReaderEvent

    // ==================== Brightness Events ====================

    /**
     * Set screen brightness
     * @param brightness Brightness level (0.1 - 1.5)
     */
    data class OnBrightnessChange(val brightness: Float) : ReaderEvent

    /** Increase brightness */
    data object BrightnessUp : ReaderEvent

    /** Decrease brightness */
    data object BrightnessDown : ReaderEvent

    // ==================== Auto-scroll Events ====================

    /**
     * Set auto-scroll speed
     * @param speed Pixels per second
     */
    data class OnAutoScrollSpeedChange(val speed: Float) : ReaderEvent

    /** Increase auto-scroll speed */
    data object AutoScrollSpeedUp : ReaderEvent

    /** Decrease auto-scroll speed */
    data object AutoScrollSpeedDown : ReaderEvent

    // ==================== Settings Events ====================

    /**
     * Toggle a boolean setting
     * @param setting Setting identifier
     */
    data class ToggleSetting(val setting: ReaderSetting) : ReaderEvent

    /**
     * Update tap zone configuration
     * @param config New tap zone configuration
     */
    data class UpdateTapZones(val config: TapZoneConfig) : ReaderEvent

    // ==================== Bookmark Events ====================

    /** Toggle bookmark on current page */
    data object ToggleBookmark : ReaderEvent

    // ==================== Share Events ====================

    /** Share current page */
    data object SharePage : ReaderEvent

    // ==================== Error Events ====================

    /** Dismiss current error */
    data object DismissError : ReaderEvent

    /** Retry failed operation */
    data object Retry : ReaderEvent

    // ==================== Color Filter Events ====================

    /**
     * Set the color filter overlay mode.
     * @param mode The new [ColorFilterMode] to apply.
     */
    data class SetColorFilterMode(val mode: ColorFilterMode) : ReaderEvent

    /**
     * Set the custom tint color (ARGB packed int) used when mode is [ColorFilterMode.CUSTOM_TINT].
     */
    data class SetCustomTintColor(val color: Long) : ReaderEvent

    companion object {
        /** Default zoom increment for zoom in/out operations */
        const val ZOOM_INCREMENT = 0.25f

        /** Minimum zoom level allowed */
        const val MIN_ZOOM = 0.5f

        /** Maximum zoom level allowed */
        const val MAX_ZOOM = 5.0f

        /** Brightness adjustment increment */
        const val BRIGHTNESS_INCREMENT = 0.1f

        /** Auto-scroll speed increment (pixels per second) */
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
    AUTO_CROP,
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
