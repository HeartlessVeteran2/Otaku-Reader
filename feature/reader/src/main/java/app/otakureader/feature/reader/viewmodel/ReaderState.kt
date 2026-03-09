package app.otakureader.feature.reader.viewmodel

import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.model.TapZoneConfig

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
    val incognitoMode: Boolean = false
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
}

/**
 * Speed settings for smart panel navigation
 */
enum class SmartPanelSpeed {
    SLOW,    // Careful, detailed navigation
    NORMAL,  // Balanced speed
    FAST     // Quick panel transitions
}
