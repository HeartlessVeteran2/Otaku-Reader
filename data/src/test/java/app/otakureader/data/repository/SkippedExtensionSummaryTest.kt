package app.otakureader.data.repository

import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.loader.ExtensionLoadResult
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the diagnostics for extensions dropped during a source refresh.
 *
 * `refreshSources()` used to discard every non-Success load result with no logging at all, so a
 * completely broken extension pipeline produced an empty source list and nothing in logcat
 * explaining why. These tests pin the summary that replaced that silence — in particular that it
 * stays readable when *every* extension fails, which is the case that actually matters, since
 * that is what a version-gate or trust-store problem looks like.
 */
class SkippedExtensionSummaryTest {

    private fun error(
        pkgName: String,
        reason: ExtensionLoadResult.Error.Reason,
    ) = ExtensionLoadResult.Error(
        // Real loader messages interpolate the package name — that detail is the whole reason
        // grouping cannot key on the message.
        message = "Unsupported lib version 1.9 for $pkgName (expected 1.4..1.7)",
        reason = reason,
    )

    private fun untrusted(pkgName: String): ExtensionLoadResult.Untrusted {
        val extension = mockk<Extension>()
        every { extension.pkgName } returns pkgName
        return ExtensionLoadResult.Untrusted(extension)
    }

    @Test
    fun `no results produces no output`() {
        assertEquals(emptyList<String>(), summarizeSkippedExtensions(emptyList()))
    }

    @Test
    fun `successful loads are not reported`() {
        val success = mockk<ExtensionLoadResult.Success>()

        assertEquals(emptyList<String>(), summarizeSkippedExtensions(listOf(success)))
    }

    /**
     * The regression this function exists for. Keying on the message would emit one line per
     * package, because each message names its own package — reproducing the flood the grouping
     * is supposed to prevent.
     */
    @Test
    fun `many failures sharing a reason collapse to a single line`() {
        val results = (1..50).map {
            error("eu.kanade.tachiyomi.extension.en.source$it", ExtensionLoadResult.Error.Reason.UNSUPPORTED_LIB_VERSION)
        }

        val lines = summarizeSkippedExtensions(results)

        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("50 extension(s) failed to load [UNSUPPORTED_LIB_VERSION]"))
    }

    @Test
    fun `distinct reasons are reported separately`() {
        val results = listOf(
            error("a", ExtensionLoadResult.Error.Reason.UNSUPPORTED_LIB_VERSION),
            error("b", ExtensionLoadResult.Error.Reason.UNSUPPORTED_LIB_VERSION),
            error("c", ExtensionLoadResult.Error.Reason.NO_VALID_SOURCES),
        )

        val lines = summarizeSkippedExtensions(results)

        assertEquals(2, lines.size)
        assertTrue(lines.any { it.startsWith("2 extension(s) failed to load [UNSUPPORTED_LIB_VERSION]") })
        assertTrue(lines.any { it.startsWith("1 extension(s) failed to load [NO_VALID_SOURCES]") })
    }

    /**
     * Untrusted is not an error — it is user-recoverable from the extensions screen — so it is
     * reported on its own line, naming the packages the user has to act on.
     */
    @Test
    fun `untrusted extensions are reported separately and named`() {
        val results = listOf(
            untrusted("eu.kanade.tachiyomi.extension.en.alpha"),
            untrusted("eu.kanade.tachiyomi.extension.en.beta"),
            error("gamma", ExtensionLoadResult.Error.Reason.NO_VALID_SOURCES),
        )

        val lines = summarizeSkippedExtensions(results)

        assertEquals(2, lines.size)
        val untrustedLine = lines.first { it.contains("not trusted") }
        assertTrue(untrustedLine.startsWith("2 extension(s)"))
        assertTrue(untrustedLine.contains("eu.kanade.tachiyomi.extension.en.alpha"))
        assertTrue(untrustedLine.contains("eu.kanade.tachiyomi.extension.en.beta"))
    }

    @Test
    fun `an overlong message is truncated`() {
        val results = listOf(
            ExtensionLoadResult.Error(
                message = "x".repeat(5_000),
                reason = ExtensionLoadResult.Error.Reason.UNKNOWN,
            ),
        )

        val line = summarizeSkippedExtensions(results).single()

        // Prefix plus at most the capped sample — well short of the raw 5,000 characters.
        assertTrue("line was ${line.length} chars", line.length < 300)
    }
}
