package app.otakureader.core.common.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the lookup rules for page-image headers.
 *
 * The behaviour that matters is what a *missing* header costs: a host with hotlink protection
 * answers a `Referer`-less request with 403, which the reader renders as a blank page
 * indistinguishable from a dead link. So these tests are mostly about the paths where a naive
 * implementation quietly returns nothing.
 */
class PageImageHeadersTest {

    private lateinit var headers: PageImageHeaders

    @Before
    fun setUp() {
        headers = PageImageHeaders()
    }

    @Test
    fun `a registered host supplies the referer for its images`() {
        headers.registerReferer(
            sourceBaseUrl = "https://example.test",
            pageUrls = listOf("https://cdn.example.test/1.jpg"),
        )

        assertEquals(
            mapOf("Referer" to "https://example.test"),
            headers.headersFor("https://cdn.example.test/1.jpg"),
        )
    }

    /**
     * The reason the fallback is keyed by host and not by exact URL. CDNs append cache-busting
     * query parameters and redirects land on sibling paths, so an exact-match-only registry
     * would attach the Referer to the first request and silently drop it from the rest.
     */
    @Test
    fun `the referer survives a url the source never handed us verbatim`() {
        headers.registerReferer("https://example.test", listOf("https://cdn.example.test/1.jpg"))

        val withQuery = headers.headersFor("https://cdn.example.test/1.jpg?token=abc&v=2")
        val otherPath = headers.headersFor("https://cdn.example.test/deep/9.jpg")

        assertEquals("https://example.test", withQuery["Referer"])
        assertEquals("https://example.test", otherPath["Referer"])
    }

    @Test
    fun `an unregistered host contributes nothing`() {
        headers.registerReferer("https://example.test", listOf("https://cdn.example.test/1.jpg"))

        assertTrue(headers.headersFor("https://elsewhere.test/1.jpg").isEmpty())
    }

    /**
     * A source supplying an auth header must still get the Referer — replacing rather than
     * merging would trade one 403 for another.
     */
    @Test
    fun `source headers merge over the host referer rather than replacing it`() {
        headers.registerReferer("https://example.test", listOf("https://cdn.example.test/1.jpg"))
        headers.registerPageHeaders(
            mapOf("https://cdn.example.test/1.jpg" to mapOf("Authorization" to "Bearer x")),
        )

        val result = headers.headersFor("https://cdn.example.test/1.jpg")

        assertEquals("https://example.test", result["Referer"])
        assertEquals("Bearer x", result["Authorization"])
    }

    /** A source that knows its own Referer requirement must be able to override ours. */
    @Test
    fun `a source supplied referer wins over the derived one`() {
        headers.registerReferer("https://example.test", listOf("https://cdn.example.test/1.jpg"))
        headers.registerPageHeaders(
            mapOf("https://cdn.example.test/1.jpg" to mapOf("Referer" to "https://override.test")),
        )

        assertEquals(
            "https://override.test",
            headers.headersFor("https://cdn.example.test/1.jpg")["Referer"],
        )
    }

    @Test
    fun `per page headers apply only to their own page`() {
        headers.registerPageHeaders(
            mapOf("https://cdn.example.test/1.jpg" to mapOf("Authorization" to "Bearer x")),
        )

        assertNull(headers.headersFor("https://cdn.example.test/2.jpg")["Authorization"])
    }

    /** A malformed URL must return empty rather than throwing into the image pipeline. */
    @Test
    fun `a malformed url yields no headers instead of failing`() {
        headers.registerReferer("https://example.test", listOf("not a url at all"))

        assertTrue(headers.headersFor("also not a url").isEmpty())
    }

    /** A source with no baseUrl has no Referer to give; registering an empty one is worse. */
    @Test
    fun `a blank base url registers nothing`() {
        headers.registerReferer("", listOf("https://cdn.example.test/1.jpg"))

        assertTrue(headers.headersFor("https://cdn.example.test/1.jpg").isEmpty())
    }

    /**
     * The registry lives for the life of the app, so it has to be bounded — a long reading
     * session would otherwise accumulate an entry per page forever.
     */
    @Test
    fun `page entries are bounded`() {
        val overflow = 2_500
        headers.registerPageHeaders(
            (1..overflow).associate { "https://cdn.example.test/$it.jpg" to mapOf("K" to "$it") },
        )

        // The most recent entry survives; the oldest is evicted.
        assertEquals("$overflow", headers.headersFor("https://cdn.example.test/$overflow.jpg")["K"])
        assertNull(headers.headersFor("https://cdn.example.test/1.jpg")["K"])
    }

    /**
     * Hosts are bounded too, and separately — one host covers a whole source, so the ceiling is
     * much lower and must not be consumed by page-level churn.
     */
    @Test
    fun `host entries are bounded independently of page entries`() {
        repeat(100) { i ->
            headers.registerReferer("https://source$i.test", listOf("https://cdn$i.test/1.jpg"))
        }

        assertEquals("https://source99.test", headers.headersFor("https://cdn99.test/1.jpg")["Referer"])
        assertTrue(headers.headersFor("https://cdn0.test/1.jpg").isEmpty())
    }
}
