package app.otakureader.feature.reader.panel

import android.graphics.Bitmap
import android.graphics.Color
import app.otakureader.feature.reader.model.ComicPanel
import app.otakureader.feature.reader.model.PanelBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Detects comic panels in manga/comic pages using image processing.
 *
 * The algorithm works by:
 * 1. Converting image to grayscale
 * 2. Applying edge detection to find panel boundaries
 * 3. Identifying rectangular regions (panels)
 * 4. Filtering and sorting panels by reading order
 */
class PanelDetector @Inject constructor() {

    /**
     * Detect panels in a manga page image.
     *
     * @param bitmap The page image to analyze
     * @param config Configuration for panel detection sensitivity
     * @return List of detected panels sorted by reading order (top to bottom, right to left for manga)
     */
    suspend fun detectPanels(
        bitmap: Bitmap,
        config: PanelDetectionConfig = PanelDetectionConfig()
    ): List<ComicPanel> = withContext(Dispatchers.Default) {
        try {
            val width = bitmap.width
            val height = bitmap.height

            if (width == 0 || height == 0) return@withContext emptyList()

            // Convert to grayscale for edge detection
            val grayscale = convertToGrayscale(bitmap)

            // Detect edges (panel boundaries) using gradient-based approach
            val edges = detectEdges(grayscale, width, height, config)

            // Find rectangular regions (panels) in the edge-detected image
            val regions = findRectangularRegions(edges, width, height, config)

            // Filter out noise and small regions
            val validPanels = filterValidPanels(regions, width, height, config)

            // Sort panels by reading order (top-to-bottom, right-to-left for manga)
            val sortedPanels = sortPanelsByReadingOrder(validPanels, config.isRightToLeft)

            // Assign panel IDs and confidence scores
            sortedPanels.mapIndexed { index, bounds ->
                ComicPanel(
                    id = index,
                    bounds = bounds,
                    confidence = calculateConfidence(bounds, width, height)
                )
            }
        } catch (e: Exception) {
            // Return empty list on error - graceful fallback
            emptyList()
        }
    }

    /**
     * Convert bitmap to grayscale pixel array
     */
    private fun convertToGrayscale(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // Standard grayscale conversion
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = gray
        }

        return pixels
    }

    /**
     * Detect edges in grayscale image using Sobel-like gradient detection
     */
    private fun detectEdges(
        grayscale: IntArray,
        width: Int,
        height: Int,
        config: PanelDetectionConfig
    ): BooleanArray {
        val edges = BooleanArray(width * height)
        val threshold = config.edgeThreshold

        // Scan for significant intensity changes (edges)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val current = grayscale[idx]

                // Calculate gradient in x and y directions
                val gx = kotlin.math.abs(grayscale[idx + 1] - grayscale[idx - 1])
                val gy = kotlin.math.abs(grayscale[idx + width] - grayscale[idx - width])

                // Combine gradients
                val gradient = max(gx, gy)

                edges[idx] = gradient > threshold
            }
        }

        return edges
    }

    /**
     * Find rectangular regions (potential panels) using horizontal and vertical line detection
     */
    private fun findRectangularRegions(
        edges: BooleanArray,
        width: Int,
        height: Int,
        config: PanelDetectionConfig
    ): List<PanelBounds> {
        val panels = mutableListOf<PanelBounds>()

        // Detect horizontal lines (panel separators)
        val horizontalLines = detectHorizontalLines(edges, width, height, config)

        // Detect vertical lines (panel separators)
        val verticalLines = detectVerticalLines(edges, width, height, config)

        // Create panels from line intersections
        // Start with full page if no lines detected
        if (horizontalLines.isEmpty() && verticalLines.isEmpty()) {
            return emptyList() // No clear panel structure - use fallback
        }

        // Add boundaries for full page
        val hLines = (listOf(0, height - 1) + horizontalLines).distinct().sorted()
        val vLines = (listOf(0, width - 1) + verticalLines).distinct().sorted()

        // Create panels from grid of lines
        for (i in 0 until hLines.size - 1) {
            for (j in 0 until vLines.size - 1) {
                val top = hLines[i].toFloat()
                val bottom = hLines[i + 1].toFloat()
                val left = vLines[j].toFloat()
                val right = vLines[j + 1].toFloat()

                panels.add(
                    PanelBounds(
                        left = left / width,
                        top = top / height,
                        right = right / width,
                        bottom = bottom / height
                    )
                )
            }
        }

        return panels
    }

    /**
     * Detect horizontal lines in edge-detected image
     */
    private fun detectHorizontalLines(
        edges: BooleanArray,
        width: Int,
        height: Int,
        config: PanelDetectionConfig
    ): List<Int> {
        val lines = mutableListOf<Int>()
        val minLineLength = (width * config.minLineLengthPercent).toInt()

        for (y in 0 until height) {
            var edgeCount = 0
            for (x in 0 until width) {
                if (edges[y * width + x]) {
                    edgeCount++
                }
            }

            // If enough edges in this row, it's a horizontal line
            if (edgeCount >= minLineLength) {
                lines.add(y)
            }
        }

        // Merge nearby lines
        return mergeNearbyLines(lines, config.lineMergeThreshold)
    }

    /**
     * Detect vertical lines in edge-detected image
     */
    private fun detectVerticalLines(
        edges: BooleanArray,
        width: Int,
        height: Int,
        config: PanelDetectionConfig
    ): List<Int> {
        val lines = mutableListOf<Int>()
        val minLineLength = (height * config.minLineLengthPercent).toInt()

        for (x in 0 until width) {
            var edgeCount = 0
            for (y in 0 until height) {
                if (edges[y * width + x]) {
                    edgeCount++
                }
            }

            // If enough edges in this column, it's a vertical line
            if (edgeCount >= minLineLength) {
                lines.add(x)
            }
        }

        // Merge nearby lines
        return mergeNearbyLines(lines, config.lineMergeThreshold)
    }

    /**
     * Merge lines that are close together
     */
    private fun mergeNearbyLines(lines: List<Int>, threshold: Int): List<Int> {
        if (lines.isEmpty()) return emptyList()

        val merged = mutableListOf<Int>()
        var currentGroup = mutableListOf(lines[0])

        for (i in 1 until lines.size) {
            if (lines[i] - lines[i - 1] <= threshold) {
                currentGroup.add(lines[i])
            } else {
                // Take average of group
                merged.add(currentGroup.average().toInt())
                currentGroup = mutableListOf(lines[i])
            }
        }

        // Add last group
        if (currentGroup.isNotEmpty()) {
            merged.add(currentGroup.average().toInt())
        }

        return merged
    }

    /**
     * Filter out invalid panels (too small, too large, etc.)
     */
    private fun filterValidPanels(
        panels: List<PanelBounds>,
        width: Int,
        height: Int,
        config: PanelDetectionConfig
    ): List<PanelBounds> {
        return panels.filter { panel ->
            val panelWidth = panel.width
            val panelHeight = panel.height

            // Filter by size
            val isBigEnough = panelWidth >= config.minPanelWidthPercent &&
                              panelHeight >= config.minPanelHeightPercent
            val isNotTooLarge = panelWidth <= config.maxPanelWidthPercent &&
                                panelHeight <= config.maxPanelHeightPercent

            isBigEnough && isNotTooLarge
        }
    }

    /**
     * Sort panels by reading order (top-to-bottom, then right-to-left for manga)
     */
    private fun sortPanelsByReadingOrder(
        panels: List<PanelBounds>,
        isRightToLeft: Boolean
    ): List<PanelBounds> {
        return panels.sortedWith(
            compareBy<PanelBounds> { it.top }
                .thenBy { if (isRightToLeft) -it.left else it.left }
        )
    }

    /**
     * Calculate confidence score for a detected panel
     */
    private fun calculateConfidence(bounds: PanelBounds, width: Int, height: Int): Float {
        // Base confidence on panel size and aspect ratio
        val area = bounds.width * bounds.height
        val aspectRatio = bounds.width / bounds.height.coerceAtLeast(0.01f)

        // Panels should have reasonable aspect ratios (not too thin)
        val aspectScore = when {
            aspectRatio in 0.2f..5.0f -> 1.0f
            aspectRatio in 0.1f..10.0f -> 0.7f
            else -> 0.5f
        }

        // Panels should be reasonably sized
        val sizeScore = when {
            area in 0.05f..0.9f -> 1.0f
            area in 0.02f..0.95f -> 0.8f
            else -> 0.6f
        }

        return (aspectScore + sizeScore) / 2.0f
    }
}

/**
 * Configuration for panel detection algorithm
 */
data class PanelDetectionConfig(
    /** Threshold for edge detection (0-255) */
    val edgeThreshold: Int = 30,

    /** Minimum line length as percentage of image dimension (0.0-1.0) */
    val minLineLengthPercent: Float = 0.4f,

    /** Distance threshold for merging nearby lines (pixels) */
    val lineMergeThreshold: Int = 10,

    /** Minimum panel width as percentage of image width (0.0-1.0) */
    val minPanelWidthPercent: Float = 0.1f,

    /** Minimum panel height as percentage of image height (0.0-1.0) */
    val minPanelHeightPercent: Float = 0.1f,

    /** Maximum panel width as percentage of image width (0.0-1.0) */
    val maxPanelWidthPercent: Float = 0.95f,

    /** Maximum panel height as percentage of image height (0.0-1.0) */
    val maxPanelHeightPercent: Float = 0.95f,

    /** Reading direction (true for manga, false for Western comics) */
    val isRightToLeft: Boolean = true
)
