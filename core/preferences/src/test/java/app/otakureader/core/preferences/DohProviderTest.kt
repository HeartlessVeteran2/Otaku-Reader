package app.otakureader.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the DoH selection survives being written to disk and read back (#1208).
 *
 * Stored by name rather than ordinal, and this is what holds that decision in place: with ordinals,
 * inserting a provider into the middle of the enum would silently repoint every existing user's
 * setting at a different company's resolver — a privacy change nobody asked for and nobody would
 * see. The round-trip test fails if someone switches the encoding.
 */
class DohProviderTest {

    @Test
    fun `every provider round-trips through its stored name`() {
        DohProvider.entries.forEach { provider ->
            assertEquals(provider, DohProvider.fromName(provider.name))
        }
    }

    /**
     * A value written by a build that had a provider this one does not falls back to the system
     * resolver. Failing to launch, or picking an arbitrary neighbour, are both worse: this is a
     * preference, and "off" is the behaviour the app had before the setting existed.
     */
    @Test
    fun `an unknown stored value falls back to off`() {
        assertEquals(DohProvider.OFF, DohProvider.fromName("SOME_REMOVED_PROVIDER"))
    }

    @Test
    fun `a missing value falls back to off`() {
        assertEquals(DohProvider.OFF, DohProvider.fromName(null))
    }

    /** OFF means "no DoH endpoint", which is how `PreferenceDns` picks the system resolver. */
    @Test
    fun `off has no endpoint and every other provider does`() {
        assertNull(DohProvider.OFF.url)
        DohProvider.entries.filter { it != DohProvider.OFF }.forEach { provider ->
            val url = provider.url
            assertTrue("${provider.name} must have an endpoint", !url.isNullOrBlank())
            assertTrue("${provider.name} must be https", url!!.startsWith("https://"))
        }
    }
}
