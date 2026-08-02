package app.otakureader.core.js.client

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two rules that had to be reimplemented when automatic redirect following was
 * disabled to close the SSRF path.
 *
 * Taking redirects away from OkHttp meant inheriting the rules it applied silently. Both of
 * these were missed on the first attempt, and neither is cosmetic: one leaks credentials to an
 * attacker-chosen host, the other can replay a POST body as a side effect.
 */
class RedirectPolicyTest {

    private fun url(value: String) = value.toHttpUrl()

    // --- origin changes -------------------------------------------------------------------

    @Test
    fun `same origin is not an origin change`() {
        assertFalse(isOriginChange(url("https://example.org/a"), url("https://example.org/b")))
    }

    @Test
    fun `a different host is an origin change`() {
        assertTrue(isOriginChange(url("https://example.org/a"), url("https://evil.test/a")))
    }

    /** A subdomain is a different origin — cookies for the parent must not follow it. */
    @Test
    fun `a subdomain is an origin change`() {
        assertTrue(isOriginChange(url("https://example.org/a"), url("https://cdn.example.org/a")))
    }

    @Test
    fun `a different port is an origin change`() {
        assertTrue(isOriginChange(url("https://example.org/a"), url("https://example.org:8443/a")))
    }

    @Test
    fun `a different scheme is an origin change`() {
        assertTrue(isOriginChange(url("https://example.org/a"), url("http://example.org/a")))
    }

    /** The default port is implicit, so naming it explicitly is still the same origin. */
    @Test
    fun `an explicit default port is not an origin change`() {
        assertTrue(
            "same origin expected",
            !isOriginChange(url("https://example.org/a"), url("https://example.org:443/b")),
        )
    }

    // --- method changes --------------------------------------------------------------------

    @Test
    fun `301 302 and 303 downgrade the follow-up to GET`() {
        listOf(301, 302, 303).forEach {
            assertTrue("$it should change the method", changesMethodToGet(it))
        }
    }

    /**
     * 307 and 308 exist precisely to preserve the method and body. Treating them like 302 would
     * silently turn a POST into a GET and lose the payload.
     */
    @Test
    fun `307 and 308 preserve the method`() {
        listOf(307, 308).forEach {
            assertFalse("$it must preserve the method", changesMethodToGet(it))
        }
    }

    @Test
    fun `non-redirect codes do not change the method`() {
        listOf(200, 204, 304, 400, 500).forEach {
            assertFalse("$it is not a method-changing redirect", changesMethodToGet(it))
        }
    }
}
