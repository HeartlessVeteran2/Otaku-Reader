package app.otakureader.feature.reader.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Represents a single page in the manga reader.
 * Reader-specific UI models stay here; shared domain models (ReaderMode, ReadingDirection,
 * ImageQuality, ColorFilterMode, TapZoneConfig) live in :domain.
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
    val thumbnails: List<ThumbnailItem> = emptyList(),
    val selectedIndex: Int = 0,
    val gridSpanCount: Int = 3,
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
