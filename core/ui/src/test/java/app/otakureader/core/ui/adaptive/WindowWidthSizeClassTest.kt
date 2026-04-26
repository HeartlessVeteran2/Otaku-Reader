package app.otakureader.core.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [WindowWidthSizeClass] breakpoint logic used to drive
 * adaptive (phone vs. tablet/DeX) layouts across the app.
 *
 * The composable [rememberWindowWidthSizeClass] is exercised indirectly via its
 * pure helper [fromWidthDp]; the composition-aware wrapper only adds remember()
 * over `LocalConfiguration.screenWidthDp`.
 */
class WindowWidthSizeClassTest {

    @Test
    fun fromWidthDp_smallPhoneWidth_isCompact() {
        // Typical phone portrait widths
        assertEquals(WindowWidthSizeClass.Compact, fromWidthDp(360))
        assertEquals(WindowWidthSizeClass.Compact, fromWidthDp(411))
        assertEquals(WindowWidthSizeClass.Compact, fromWidthDp(599))
    }

    @Test
    fun fromWidthDp_mediumWidth_isMedium() {
        // 600 dp is the lower MD3 medium boundary (small tablet / large phone landscape)
        assertEquals(WindowWidthSizeClass.Medium, fromWidthDp(600))
        assertEquals(WindowWidthSizeClass.Medium, fromWidthDp(720))
        assertEquals(WindowWidthSizeClass.Medium, fromWidthDp(839))
    }

    @Test
    fun fromWidthDp_expandedWidth_isExpanded() {
        // Tablet landscape, Samsung DeX (1280–1920 dp), ChromeOS, desktop
        assertEquals(WindowWidthSizeClass.Expanded, fromWidthDp(840))
        assertEquals(WindowWidthSizeClass.Expanded, fromWidthDp(1280))
        assertEquals(WindowWidthSizeClass.Expanded, fromWidthDp(1920))
    }

    @Test
    fun fromWidthDp_zeroOrNegative_isCompact() {
        // Defensive: degenerate configurations should fall back to phone layout
        assertEquals(WindowWidthSizeClass.Compact, fromWidthDp(0))
        assertEquals(WindowWidthSizeClass.Compact, fromWidthDp(-1))
    }

    @Test
    fun isExpanded_extension_matchesEnumValue() {
        assertTrue(WindowWidthSizeClass.Expanded.isExpanded)
        assertFalse(WindowWidthSizeClass.Medium.isExpanded)
        assertFalse(WindowWidthSizeClass.Compact.isExpanded)
    }

    @Test
    fun isAtLeastMedium_extension_treatsCompactAsBelow() {
        assertTrue(WindowWidthSizeClass.Expanded.isAtLeastMedium)
        assertTrue(WindowWidthSizeClass.Medium.isAtLeastMedium)
        assertFalse(WindowWidthSizeClass.Compact.isAtLeastMedium)
    }
}
