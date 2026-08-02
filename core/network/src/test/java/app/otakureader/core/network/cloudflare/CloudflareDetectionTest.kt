package app.otakureader.core.network.cloudflare

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers when a response counts as a Cloudflare challenge.
 *
 * Both directions carry a real cost, which is why the detector requires two signals rather than
 * one. Too eager and the app throws a WebView at ordinary 403s — a permission error on a tracker
 * API becomes a browser window the user cannot make sense of. Too conservative and a
 * Cloudflare-protected source keeps failing silently, which is the bug this whole flow exists to
 * fix.
 */
class CloudflareDetectionTest {

    private fun response(code: Int, server: String?): Response =
        Response.Builder()
            .request(Request.Builder().url("https://example.test/manga").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .apply { server?.let { header("Server", it) } }
            .build()

    @Test
    fun `503 from cloudflare is a challenge`() {
        assertTrue(response(503, "cloudflare").isCloudflareChallenge())
    }

    @Test
    fun `403 from cloudflare is a challenge`() {
        assertTrue(response(403, "cloudflare").isCloudflareChallenge())
    }

    @Test
    fun `the nginx variant is recognised`() {
        assertTrue(response(403, "cloudflare-nginx").isCloudflareChallenge())
    }

    @Test
    fun `the server header is matched case-insensitively`() {
        assertTrue(response(503, "Cloudflare").isCloudflareChallenge())
    }

    /**
     * The reason the `Server` header is required. Plenty of APIs answer 403 for ordinary
     * permission reasons, and opening a WebView at each one would be worse than the problem.
     */
    @Test
    fun `an ordinary 403 is not a challenge`() {
        assertFalse(response(403, "nginx").isCloudflareChallenge())
        assertFalse(response(403, null).isCloudflareChallenge())
    }

    /**
     * The reason the status code is required. Most Cloudflare-fronted sites serve *every*
     * response with this header, so matching on it alone would treat successful page loads as
     * challenges.
     */
    @Test
    fun `a successful response from cloudflare is not a challenge`() {
        assertFalse(response(200, "cloudflare").isCloudflareChallenge())
        assertFalse(response(404, "cloudflare").isCloudflareChallenge())
    }

    @Test
    fun `a server error that is not a challenge code is ignored`() {
        assertFalse(response(500, "cloudflare").isCloudflareChallenge())
    }
}
