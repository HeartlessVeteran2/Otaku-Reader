package app.otakureader.domain.model

/**
 * Reading modes supported by the reader.
 * Placed in :domain so that both :feature:reader and :feature:settings can reference
 * the type without creating a cross-feature dependency.
 */
enum class ReaderMode {
    SINGLE_PAGE,      // Standard single page view
    DUAL_PAGE,        // Two pages side by side (spreads)
    WEBTOON,          // Vertical continuous scrolling
    SMART_PANELS;     // Navigate by detected comic panels

    companion object {
        fun default(): ReaderMode = SINGLE_PAGE

        fun displayNames(): Map<ReaderMode, String> = mapOf(
            SINGLE_PAGE to "Single Page",
            DUAL_PAGE to "Dual Page",
            WEBTOON to "Webtoon",
            SMART_PANELS to "Smart Panels"
        )
    }
}

/**
 * Reading directions.
 */
enum class ReadingDirection {
    LTR,     // Left to Right (Western comics)
    RTL,     // Right to Left (Manga)
    VERTICAL // Top to Bottom (Webtoon)
}

/**
 * Tap zone actions.
 */
enum class TapZoneAction {
    PREVIOUS,
    NEXT,
    MENU,
    NONE
}

/**
 * Configuration for tap zones on the reader screen.
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
 * @param displayName User-facing label for display in Settings.
 */
enum class ImageQuality(
    val pixels: Int? = null,
    val displayName: String,
) {
    /** Decode at full resolution. Best quality, highest memory usage. */
    ORIGINAL(displayName = "Original"),

    /** Downscale to 1080 px on the longer side. Sharp on most displays. */
    HIGH(pixels = 1080, displayName = "High"),

    /** Downscale to 720 px on the longer side. Good balance of quality and memory. */
    MEDIUM(pixels = 720, displayName = "Medium"),

    /** Downscale to 480 px on the longer side. Lowest memory; suitable for slow connections. */
    LOW(pixels = 480, displayName = "Low");

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
}
