package app.otakureader.feature.reader.panel

import android.graphics.Bitmap
import android.graphics.Color
import app.otakureader.feature.reader.model.ReadingDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for PanelDetector
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PanelDetectorTest {

    private lateinit var panelDetector: PanelDetector

    @Before
    fun setup() {
        panelDetector = PanelDetector()
    }

    @Test
    fun `detectPanels returns empty list for empty bitmap`() = runTest {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val config = PanelDetectionConfig()

        val panels = panelDetector.detectPanels(bitmap, config)

        assertTrue(panels.isEmpty())
        bitmap.recycle()
    }

    @Test
    fun `detectPanels handles simple two-panel layout`() = runTest {
        // Create a 200x200 bitmap with vertical separator in the middle
        val bitmap = createSimpleTwoPanelBitmap()
        val config = PanelDetectionConfig(
            edgeThreshold = 20,
            minLineLengthPercent = 0.3f,
            minPanelWidthPercent = 0.1f,
            maxPanelHeightPercent = 1.0f
        )

        val panels = panelDetector.detectPanels(bitmap, config)

        // Should detect at least one panel (may detect 2-4 depending on edge detection)
        assertTrue("Expected panels to be detected", panels.isNotEmpty())

        // All panels should have valid bounds
        panels.forEach { panel ->
            assertTrue("Panel width should be positive", panel.bounds.width > 0)
            assertTrue("Panel height should be positive", panel.bounds.height > 0)
            assertTrue("Panel confidence should be between 0 and 1",
                panel.confidence in 0.0f..1.0f)
        }

        bitmap.recycle()
    }

    @Test
    fun `detectPanels sorts panels by reading order for RTL`() = runTest {
        val bitmap = createGridPanelBitmap()
        val config = PanelDetectionConfig(
            isRightToLeft = true,
            edgeThreshold = 20,
            minLineLengthPercent = 0.3f
        )

        val panels = panelDetector.detectPanels(bitmap, config)

        if (panels.size >= 2) {
            // For RTL, panels on the right should come first
            // (higher Y = later, higher X = earlier for RTL)
            for (i in 0 until panels.size - 1) {
                val current = panels[i]
                val next = panels[i + 1]

                // If same row (similar top position), right panel should come first
                if (kotlin.math.abs(current.bounds.top - next.bounds.top) < 0.1f) {
                    assertTrue(
                        "RTL panels should be ordered right-to-left in same row",
                        current.bounds.left >= next.bounds.left
                    )
                }
            }
        }

        bitmap.recycle()
    }

    @Test
    fun `detectPanels sorts panels by reading order for LTR`() = runTest {
        val bitmap = createGridPanelBitmap()
        val config = PanelDetectionConfig(
            isRightToLeft = false,
            edgeThreshold = 20,
            minLineLengthPercent = 0.3f
        )

        val panels = panelDetector.detectPanels(bitmap, config)

        if (panels.size >= 2) {
            // For LTR, panels on the left should come first
            for (i in 0 until panels.size - 1) {
                val current = panels[i]
                val next = panels[i + 1]

                // If same row (similar top position), left panel should come first
                if (kotlin.math.abs(current.bounds.top - next.bounds.top) < 0.1f) {
                    assertTrue(
                        "LTR panels should be ordered left-to-right in same row",
                        current.bounds.left <= next.bounds.left
                    )
                }
            }
        }

        bitmap.recycle()
    }

    @Test
    fun `panel bounds are normalized to 0-1 range`() = runTest {
        val bitmap = createSimpleTwoPanelBitmap()
        val config = PanelDetectionConfig(edgeThreshold = 20)

        val panels = panelDetector.detectPanels(bitmap, config)

        panels.forEach { panel ->
            assertTrue("Left bound should be 0-1", panel.bounds.left in 0.0f..1.0f)
            assertTrue("Top bound should be 0-1", panel.bounds.top in 0.0f..1.0f)
            assertTrue("Right bound should be 0-1", panel.bounds.right in 0.0f..1.0f)
            assertTrue("Bottom bound should be 0-1", panel.bounds.bottom in 0.0f..1.0f)
        }

        bitmap.recycle()
    }

    @Test
    fun `detectPanels filters out panels that are too small`() = runTest {
        val bitmap = createBitmapWithSmallNoise()
        val config = PanelDetectionConfig(
            edgeThreshold = 20,
            minPanelWidthPercent = 0.2f,
            minPanelHeightPercent = 0.2f
        )

        val panels = panelDetector.detectPanels(bitmap, config)

        // All panels should meet minimum size requirements
        panels.forEach { panel ->
            assertTrue("Panel width should be >= 20%", panel.bounds.width >= 0.2f)
            assertTrue("Panel height should be >= 20%", panel.bounds.height >= 0.2f)
        }

        bitmap.recycle()
    }

    // Helper: Create a simple bitmap with vertical line in middle (2 panels)
    private fun createSimpleTwoPanelBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(200 * 200) { Color.WHITE }

        // Draw vertical black line in middle
        for (y in 0 until 200) {
            pixels[y * 200 + 100] = Color.BLACK
        }

        bitmap.setPixels(pixels, 0, 200, 0, 0, 200, 200)
        return bitmap
    }

    // Helper: Create a 2x2 grid of panels
    private fun createGridPanelBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(200 * 200) { Color.WHITE }

        // Draw vertical line at x=100
        for (y in 0 until 200) {
            pixels[y * 200 + 100] = Color.BLACK
        }

        // Draw horizontal line at y=100
        for (x in 0 until 200) {
            pixels[100 * 200 + x] = Color.BLACK
        }

        bitmap.setPixels(pixels, 0, 200, 0, 0, 200, 200)
        return bitmap
    }

    // Helper: Create bitmap with noise (small panels to be filtered)
    private fun createBitmapWithSmallNoise(): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(200 * 200) { Color.WHITE }

        // Add some small rectangles (noise)
        for (y in 10 until 20) {
            for (x in 10 until 20) {
                pixels[y * 200 + x] = Color.BLACK
            }
        }

        bitmap.setPixels(pixels, 0, 200, 0, 0, 200, 200)
        return bitmap
    }
}
