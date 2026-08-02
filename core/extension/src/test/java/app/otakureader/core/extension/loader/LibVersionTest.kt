package app.otakureader.core.extension.loader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct tests for extensions-lib version parsing and ordering.
 *
 * Tested here rather than only through the loader because every version bug in this class so far
 * has come from the parser, not the gate, and each one presented identically — a supported
 * extension refused with "Unsupported lib version". Asserting the parse and the comparison
 * separately makes the actual defect visible instead of one indistinguishable rejection.
 */
class LibVersionTest {

    // --- parsing -------------------------------------------------------------------------

    @Test
    fun `parses the conventional three-component versionName`() {
        assertEquals(LibVersion(1, 4), LibVersion.parse("1.4.19"))
    }

    /**
     * Regression: the old parser stripped the trailing component, so `"1.7"` became `"1"` → 1.0,
     * below the minimum. A current extension was refused for looking too old.
     */
    @Test
    fun `parses a two-component versionName`() {
        assertEquals(LibVersion(1, 7), LibVersion.parse("1.7"))
    }

    /** Regression: `"1.4.19.1"` previously left `"1.4.19"`, which is not a number at all. */
    @Test
    fun `parses a four-component versionName`() {
        assertEquals(LibVersion(1, 4), LibVersion.parse("1.4.19.1"))
    }

    @Test
    fun `rejects a versionName with no minor component`() {
        assertNull(LibVersion.parse("2"))
        assertNull(LibVersion.parse(""))
    }

    @Test
    fun `rejects non-numeric components`() {
        assertNull(LibVersion.parse("invalid"))
        assertNull(LibVersion.parse("v1.4"))
        assertNull(LibVersion.parse("1.x"))
    }

    @Test
    fun `rejects negative components`() {
        assertNull(LibVersion.parse("-1.4"))
        assertNull(LibVersion.parse("1.-4"))
    }

    // --- ordering ------------------------------------------------------------------------

    /**
     * The reason this is an integer pair and not a Double.
     *
     * As decimals, `"1.40"` parses to 1.4 — identical to lib 1.4 — so an unsupported extension
     * would slip through the gate and fail later inside class loading instead.
     */
    @Test
    fun `a multi-digit minor is not confused with a shorter one`() {
        val fortyth = LibVersion.parse("1.40.0")!!
        val fourth = LibVersion.parse("1.4.0")!!

        assertEquals(LibVersion(1, 40), fortyth)
        assertTrue("1.40 must sort above 1.4", fortyth > fourth)
    }

    /**
     * The direction that will actually bite. extensions-lib runs 1.7 → 1.8 → 1.9 → 1.10, and as
     * a decimal `"1.10"` parses to 1.1 — *below* the 1.4 minimum — so every lib 1.10 extension
     * would have been rejected as too old the moment upstream shipped it.
     */
    @Test
    fun `minor version ten sorts above nine, not below one point four`() {
        val ten = LibVersion.parse("1.10.0")!!

        assertTrue("1.10 must sort above 1.9", ten > LibVersion(1, 9))
        assertTrue("1.10 must sort above the 1.4 minimum", ten > LibVersion(1, 4))
        assertTrue("1.10 must sort above the 1.7 ceiling", ten > LibVersion(1, 7))
    }

    @Test
    fun `major version dominates minor`() {
        assertTrue(LibVersion(2, 0) > LibVersion(1, 99))
    }

    @Test
    fun `equal versions compare equal`() {
        assertEquals(0, LibVersion(1, 5).compareTo(LibVersion(1, 5)))
    }

    // --- the supported window ------------------------------------------------------------

    /**
     * Pins the gate against the parsed values rather than restating the constants, so a change to
     * either bound has to be deliberate.
     */
    @Test
    fun `the supported window admits 1_4 through 1_7 and nothing outside it`() {
        val min = ExtensionLoader.LIB_VERSION_MIN
        val max = ExtensionLoader.LIB_VERSION_MAX

        listOf("1.4.0", "1.5.9", "1.6.0", "1.7.42").forEach {
            val version = LibVersion.parse(it)!!
            assertTrue("$it should be inside the window", version >= min && version <= max)
        }

        listOf("1.3.9", "1.8.0", "1.10.0", "1.40.0", "2.0.1").forEach {
            val version = LibVersion.parse(it)!!
            assertTrue("$it should be outside the window", version < min || version > max)
        }
    }

    @Test
    fun `toString renders the major minor pair`() {
        assertEquals("1.10", LibVersion(1, 10).toString())
        assertEquals("1.4", LibVersion(1, 4).toString())
    }
}
