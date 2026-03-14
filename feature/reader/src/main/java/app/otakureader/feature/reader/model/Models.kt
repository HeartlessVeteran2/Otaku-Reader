package app.otakureader.feature.reader.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import app.otakureader.feature.reader.R

/**
 * Represents a single page in the manga reader
 */
data class ReaderPage(
    val index: Int,
    val imageUrl: String? = null,
    val bitmap: ImageBitmap? = null,
    val localPath: String? = null,
    val chapterName: String = "",
    val isSpread: Boolean = false,
    val pageNumber: Int = index + 1,
    val thumbnailUrl: String? = null,
    val id: String = "page_$index",
    val panels: List<ComicPanel> = emptyList()
)

/**
 * Reading modes supported by the reader
 */
enum class ReaderMode {
    SINGLE_PAGE,      // Standard single page view
    DUAL_PAGE,        // Two pages side by side (spreads)
    WEBTOON,          // Vertical continuous scrolling
    SMART_PANELS;      // Navigate by detected comic panels

    companion object {
        /** Get default mode */
        fun default(): ReaderMode = SINGLE_PAGE

        /** Get all modes as display names */
        fun displayNames(): Map<ReaderMode, String> = mapOf(
            SINGLE_PAGE to "Single Page",
            DUAL_PAGE to "Dual Page",
            WEBTOON to "Webtoon",
            SMART_PANELS to "Smart Panels"
        )
    }
}

/**
 * Reading directions
 */
enum class ReadingDirection {
    LTR,              // Left to Right (Western comics)
    RTL,              // Right to Left (Manga)
    VERTICAL          // Top to Bottom (Webtoon)
}

/**
 * Tap zone actions
 */
enum class TapZoneAction {
    PREVIOUS,
    NEXT,
    MENU,
    NONE
}

/**
 * Represents a detected comic panel
 */
data class ComicPanel(
    val id: Int,
    val bounds: PanelBounds,
    val confidence: Float
)

data class PanelBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
}

/**
 * Zoom state for tracking zoom/pan
 */
data class ZoomState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f
) {
    val isZoomed: Boolean get() = scale > 1.01f
}

/**
 * Crop configuration for border removal
 */
data class CropConfig(
    val enabled: Boolean = true,
    val threshold: Float = 0.95f,
    val minCropPercent: Float = 0.05f,
    val maxCropPercent: Float = 0.4f,
    val detectWhiteBorders: Boolean = true,
    val detectBlackBorders: Boolean = true
)

/**
 * Configuration for tap zones on the reader screen
 */
data class TapZoneConfig(
    /** Width percentage for left zone (0.0 - 1.0) */
    val leftZoneWidth: Float = 0.25f,

    /** Width percentage for center zone (0.0 - 1.0) */
    val centerZoneWidth: Float = 0.5f,

    /** Width percentage for right zone (calculated from remaining space) */
    val rightZoneWidth: Float = 0.25f,

    /** Height percentage for top zone */
    val topZoneHeight: Float = 0.2f,

    /** Height percentage for bottom zone */
    val bottomZoneHeight: Float = 0.2f,

    /** Whether tap zones are enabled */
    val enabled: Boolean = true,

    /** Invert tap zones for RTL reading */
    val invertForRtl: Boolean = true,

    /** Haptic feedback on tap */
    val hapticFeedback: Boolean = true
) {
    init {
        require(kotlin.math.abs(leftZoneWidth + centerZoneWidth + rightZoneWidth - 1.0f) < 0.001f) {
            "Tap zone widths must sum to 1.0"
        }
    }
}

/**
 * Image quality levels for page rendering.
 * Controls both the Coil decode size and the rendering filter quality.
 *
 * @param pixels Maximum dimension in pixels for Coil decode (null = full resolution).
 * @param stringRes User-facing label resource for display in Settings.
 */
enum class ImageQuality(
    val pixels: Int? = null,
    @StringRes val stringRes: Int,
) {
    /** Decode at full resolution. Best quality, highest memory usage. */
    ORIGINAL(stringRes = R.string.image_quality_original),

    /** Downscale to 1080 px on the longer side. Sharp on most displays. */
    HIGH(pixels = 1080, stringRes = R.string.image_quality_high),

    /** Downscale to 720 px on the longer side. Good balance of quality and memory. */
    MEDIUM(pixels = 720, stringRes = R.string.image_quality_medium),

    /** Downscale to 480 px on the longer side. Lowest memory; suitable for slow connections. */
    LOW(pixels = 480, stringRes = R.string.image_quality_low);

    companion object {
        fun default(): ImageQuality = ORIGINAL
    }
}

/**
 * Color filter modes for the reader overlay.
 */
enum class ColorFilterMode {
    /** No color filter applied. */
    NONE,

    /** Warm sepia tone – reduces eye strain in low light. */
    SEPIA,

    /** Desaturates the image to greyscale. */
    GRAYSCALE,

    /** Inverts all colors – useful in pitch-black environments. */
    INVERT,

    /** User-defined tint with configurable color and opacity. */
    CUSTOM_TINT;

    companion object {
        fun displayNameResId(mode: ColorFilterMode): Int = when (mode) {
            NONE -> R.string.reader_color_filter_none
            SEPIA -> R.string.reader_color_filter_sepia
            GRAYSCALE -> R.string.reader_color_filter_greyscale
            INVERT -> R.string.reader_color_filter_invert
            CUSTOM_TINT -> R.string.reader_color_filter_custom_tint
        }
    }
}

/**
 * Page transition animation types
 */
enum class PageTransition {
    SLIDE_HORIZONTAL,
    SLIDE_VERTICAL,
    FADE,
    CURL,
    NONE
}

/**
 * State for the page gallery/thumbnail view
 */
data class GalleryState(
    /** List of thumbnail URLs or page indices */
    val thumbnails: List<ThumbnailItem> = emptyList(),

    /** Currently selected thumbnail */
    val selectedIndex: Int = 0,

    /** Grid span count for layout */
    val gridSpanCount: Int = 3,

    /** Whether to show page numbers on thumbnails */
    val showPageNumbers: Boolean = true
)

/**
 * Individual thumbnail item in gallery
 */
data class ThumbnailItem(
    val pageIndex: Int,
    val thumbnailUrl: String?,
    val isCurrentPage: Boolean = false
)
