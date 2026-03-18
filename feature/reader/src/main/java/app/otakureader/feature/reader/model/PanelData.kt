package app.otakureader.feature.reader.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data models for panel analysis results from Gemini Vision API.
 * 
 * These models extend the basic ComicPanel with additional metadata
 * from AI-based panel detection, including confidence scores and
 * detected text regions.
 */

/**
 * Represents detailed panel data from AI analysis
 */
@Serializable
data class PanelData(
    val id: Int,
    val bounds: PanelBoundsData,
    val confidence: Float,
    val detectedText: List<TextRegionData> = emptyList(),
    val panelType: PanelType = PanelType.STANDARD,
    val readingOrder: Int = id
) {
    /**
     * Convert to ComicPanel for use in existing reader components
     */
    fun toComicPanel(): ComicPanel = ComicPanel(
        id = id,
        bounds = bounds.toPanelBounds(),
        confidence = confidence
    )

    companion object {
        /**
         * Create PanelData from ComicPanel
         */
        fun fromComicPanel(panel: ComicPanel): PanelData = PanelData(
            id = panel.id,
            bounds = PanelBoundsData.fromPanelBounds(panel.bounds),
            confidence = panel.confidence
        )
    }
}

/**
 * Serializable panel bounds for caching
 */
@Serializable
data class PanelBoundsData(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    /**
     * Convert to PanelBounds for use in reader
     */
    fun toPanelBounds(): PanelBounds = PanelBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )

    companion object {
        /**
         * Create PanelBoundsData from PanelBounds
         */
        fun fromPanelBounds(bounds: PanelBounds): PanelBoundsData = PanelBoundsData(
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom
        )
    }
}

/**
 * Represents a region of detected text within a panel
 */
@Serializable
data class TextRegionData(
    val bounds: PanelBoundsData,
    val confidence: Float,
    val isSpeechBubble: Boolean = false
)

/**
 * Types of panels that can be detected
 */
@Serializable
enum class PanelType {
    STANDARD,       // Regular rectangular panel
    SPLASH,         // Full-page or large splash panel
    INSET,          // Small inset panel
    BORDERLESS,     // Panel without clear borders
    DOUBLE_SPREAD,  // Panel spanning two pages
    IRREGULAR       // Non-rectangular panel
}

/**
 * Complete analysis result for a manga/comic page
 */
@Serializable
data class PageAnalysisResult(
    val imageHash: String,
    val panels: List<PanelData>,
    val pageType: PageType = PageType.STANDARD,
    val analysisTimestamp: Long = System.currentTimeMillis(),
    val readingDirection: ReadingDirection = ReadingDirection.RTL,
    val modelVersion: String = MODEL_VERSION
) {
    /**
     * Convert panels to ComicPanel list for reader integration
     */
    fun toComicPanels(): List<ComicPanel> = panels.map { it.toComicPanel() }

    /**
     * Check if analysis is stale (older than specified days)
     */
    fun isStale(maxAgeDays: Int = CACHE_MAX_AGE_DAYS): Boolean {
        val maxAgeMillis = maxAgeDays.toLong() * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - analysisTimestamp > maxAgeMillis
    }

    /**
     * Serialize to JSON string for caching
     */
    fun toJson(): String = json.encodeToString(this)

    companion object {
        const val MODEL_VERSION = "1.0.0"
        const val CACHE_MAX_AGE_DAYS = 30

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

        /**
         * Deserialize from JSON string
         */
        fun fromJson(jsonString: String): PageAnalysisResult? = try {
            json.decodeFromString<PageAnalysisResult>(jsonString)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Types of pages that can be detected
 */
@Serializable
enum class PageType {
    STANDARD,       // Regular page with panels
    SPLASH,         // Full-page illustration
    DOUBLE_SPREAD,  // Two-page spread
    TEXT_HEAVY,     // Page with lots of text
    TITLE_PAGE,     // Chapter title page
    CREDITS         // Credits/author notes page
}

/**
 * Request for panel analysis
 */
data class PanelAnalysisRequest(
    val imageUrl: String,
    val imageHash: String,
    val readingDirection: ReadingDirection = ReadingDirection.RTL,
    val mangaTitle: String? = null,
    val chapterNumber: Int? = null,
    val pageNumber: Int? = null
)

/**
 * Result of panel analysis operation
 */
sealed class PanelAnalysisResultWrapper {
    data class Success(val result: PageAnalysisResult) : PanelAnalysisResultWrapper()
    data class Error(val exception: PanelAnalysisException) : PanelAnalysisResultWrapper()
    data object NotConfigured : PanelAnalysisResultWrapper()
}

/**
 * Exceptions specific to panel analysis
 */
sealed class PanelAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotInitialized(message: String = "Panel analyzer not initialized") : PanelAnalysisException(message)
    class ApiError(message: String, cause: Throwable? = null) : PanelAnalysisException(message, cause)
    class InvalidResponse(message: String) : PanelAnalysisException(message)
    class ImageLoadError(message: String, cause: Throwable? = null) : PanelAnalysisException(message, cause)
    class CacheError(message: String, cause: Throwable? = null) : PanelAnalysisException(message, cause)
    class Timeout(message: String = "Panel analysis timed out") : PanelAnalysisException(message)
}
