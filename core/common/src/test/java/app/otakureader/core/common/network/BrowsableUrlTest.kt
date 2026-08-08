package app.otakureader.core.common.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These two helpers guard the boundary between third-party URLs and an Android `Intent`, so the
 * cases below are the point of them rather than incidental coverage.
 */
class BrowsableUrlTest {

    @Test
    fun `http and https are browsable, whatever the case`() {
        assertTrue("https://example.test/a".isBrowsableHttpUrl())
        assertTrue("http://example.test/a".isBrowsableHttpUrl())
        assertTrue("HTTPS://example.test/a".isBrowsableHttpUrl())
        assertTrue("HtTp://example.test/a".isBrowsableHttpUrl())
    }

    /** The schemes this exists to keep away from `Intent.ACTION_VIEW`. */
    @Test
    fun `other schemes are refused`() {
        assertFalse("javascript:alert(1)".isBrowsableHttpUrl())
        assertFalse("intent://scan/#Intent;scheme=zxing;end".isBrowsableHttpUrl())
        assertFalse("file:///etc/passwd".isBrowsableHttpUrl())
        assertFalse("content://com.example/secret".isBrowsableHttpUrl())
        assertFalse("mailto:someone@example.test".isBrowsableHttpUrl())
        assertFalse("data:text/html,<script>".isBrowsableHttpUrl())
    }

    /**
     * Fail closed. For a check whose job is to keep hostile schemes out, "I could not parse this"
     * has to mean no — accepting the unparseable is the expensive direction to be wrong in.
     */
    @Test
    fun `an unparseable url is refused rather than waved through`() {
        assertFalse("".isBrowsableHttpUrl())
        assertFalse("not a url at all".isBrowsableHttpUrl())
        assertFalse("http://exa mple.test".isBrowsableHttpUrl())
        assertFalse("://example.test".isBrowsableHttpUrl())
    }

    /**
     * `http:foo` and `https:///path` parse cleanly and report an http scheme with no authority at
     * all. Waved through, they become a chip that opens nothing — the dead control the fail-closed
     * rule exists to avoid.
     */
    @Test
    fun `a scheme with no authority is refused`() {
        assertFalse("http:foo".isBrowsableHttpUrl())
        assertFalse("https:///path".isBrowsableHttpUrl())
        assertFalse("https:".isBrowsableHttpUrl())
    }

    /**
     * `URI.getHost()` returns null for an internationalised name, so a host-based check would drop
     * every IDN link — turning a cosmetic labelling problem into a missing link. The predicate
     * tests the authority for exactly this reason.
     */
    @Test
    fun `an internationalised host is browsable and labelled by its host`() {
        assertTrue("https://\u4F8B\u3048.\u30C6\u30B9\u30C8/p".isBrowsableHttpUrl())
        assertEquals("\u4F8B\u3048.\u30C6\u30B9\u30C8", "https://\u4F8B\u3048.\u30C6\u30B9\u30C8/p".browsableHostOrNull())
        // Punycode parses as an ordinary host and needs no fallback.
        assertEquals("xn--r8jz45g.xn--zckzah", "https://xn--r8jz45g.xn--zckzah/p".browsableHostOrNull())
    }

    @Test
    fun `an internationalised authority still loses its userinfo and port`() {
        assertEquals(
            "\u4F8B\u3048.\u30C6\u30B9\u30C8",
            "https://user:pw@\u4F8B\u3048.\u30C6\u30B9\u30C8:8443/p".browsableHostOrNull(),
        )
    }

    /** Hostnames are case-insensitive, so the label normalises and the `www.` strip is case-blind. */
    @Test
    fun `the label is lowercased whatever case it arrived in`() {
        assertEquals("example.test", "https://WWW.Example.TEST/p".browsableHostOrNull())
        assertEquals("example.test", "https://Example.Test".browsableHostOrNull())
    }

    @Test
    fun `the host drops a www prefix and ignores path, query and port`() {
        assertEquals("example.test", "https://www.example.test/path?q=1".browsableHostOrNull())
        assertEquals("example.test", "https://example.test:8443/path".browsableHostOrNull())
        assertEquals("example.test", "https://example.test".browsableHostOrNull())
    }

    /**
     * The case that motivated using a real parser: sliced by hand, this yields `user@example.test`
     * and puts a username on screen as a chip label.
     */
    @Test
    fun `userinfo does not leak into the host label`() {
        assertEquals("example.test", "https://user:pw@example.test/path".browsableHostOrNull())
    }

    @Test
    fun `a url with no host is null rather than an exception or an empty label`() {
        assertNull("not a url at all".browsableHostOrNull())
        assertNull("mailto:someone@example.test".browsableHostOrNull())
        assertNull("".browsableHostOrNull())
    }
}
