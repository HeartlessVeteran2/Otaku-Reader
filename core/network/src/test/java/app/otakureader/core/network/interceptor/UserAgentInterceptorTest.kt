package app.otakureader.core.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Which User-Agent goes out, and — the part that matters — which one is left alone (#1208).
 */
class UserAgentInterceptorTest {

    private lateinit var server: MockWebServer
    private var current = "Otaku/1.0"

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = OkHttpClient.Builder()
        .addInterceptor(UserAgentInterceptor { current })
        .build()

    private fun sentUserAgent(builder: Request.Builder.() -> Unit = {}): String? {
        server.enqueue(MockResponse())
        val request = Request.Builder().url(server.url("/")).apply(builder).build()
        client().newCall(request).execute().close()
        return server.takeRequest().getHeader("User-Agent")
    }

    /**
     * Without this, OkHttp's own `BridgeInterceptor` fills in `okhttp/<version>`, which a fair
     * number of sites reject and which no setting could change.
     */
    @Test
    fun `a request with no User-Agent gets the configured one`() {
        assertEquals("Otaku/1.0", sentUserAgent())
    }

    /**
     * A source that sets its own has usually done so because the site requires that exact string.
     * Replacing it would break the source in the name of a preference — the opposite call from
     * `ChallengeUserAgentInterceptor`, which must overwrite because clearance is bound to the
     * identity that earned it.
     */
    @Test
    fun `a caller's own User-Agent is left alone`() {
        assertEquals("SourceSpecific/9", sentUserAgent { header("User-Agent", "SourceSpecific/9") })
    }

    /**
     * Read per call, not captured. This is the whole reason the dependency is a function: the
     * shared client is built once, so a value read at construction would leave the setting doing
     * nothing until the app was relaunched.
     */
    @Test
    fun `changing the value applies to the next request without rebuilding the client`() {
        assertEquals("Otaku/1.0", sentUserAgent())

        current = "Otaku/2.0"

        assertEquals("Otaku/2.0", sentUserAgent())
    }
}
