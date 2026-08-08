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
