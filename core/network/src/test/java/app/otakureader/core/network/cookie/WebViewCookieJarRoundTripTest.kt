package app.otakureader.core.network.cookie

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The jar driven by a real OkHttp client against a real socket.
 *
 * `WebViewCookieJarTest` calls the two methods directly, which proves the translation but not that
 * the pair *interoperates* — a jar can format and parse correctly and still never be consulted, or
 * be handed cookies OkHttp had already discarded. This runs the actual sequence the Cloudflare
 * bypass depends on: a response sets a cookie, and the next request carries it back.
 *
 * The store mimics `CookieManager` closely enough for that: it keeps what was written and returns
 * it in the `name=value; name2=value2` shape the real one produces, per host.
 */
class WebViewCookieJarRoundTripTest {

    private class RecordingStore : CookieStore {
        private val byHost = mutableMapOf<String, MutableMap<String, String>>()

        override fun get(url: String): String? = byHost[hostOf(url)]
            ?.entries
            ?.joinToString("; ") { "${it.key}=${it.value}" }
            ?.takeIf { it.isNotEmpty() }

        override fun set(url: String, value: String) {
            // CookieManager applies the attributes and keeps the pair; only the pair comes back.
            val pair = value.substringBefore(';')
            byHost.getOrPut(hostOf(url)) { linkedMapOf() }[pair.substringBefore('=')] =
                pair.substringAfter('=')
        }

        override suspend fun clear() = byHost.clear()

        private fun hostOf(url: String) = url.substringAfter("://").substringBefore('/')
    }

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().cookieJar(WebViewCookieJar(RecordingStore())).build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun get(path: String) =
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()

    /**
     * The sequence the whole feature rests on. Before this jar existed the shared client used
     * `CookieJar.NO_COOKIES`, so the second request went out bare and a solved Cloudflare challenge
     * bought nothing.
     */
    @Test
    fun `a cookie set on one response is sent on the next request`() {
        server.enqueue(MockResponse().setHeader("Set-Cookie", "cf_clearance=abc123; Path=/"))
        server.enqueue(MockResponse())

        get("/challenge")
        server.takeRequest()
        get("/manga/1")

        assertEquals("cf_clearance=abc123", server.takeRequest().getHeader("Cookie"))
    }

    /** Several cookies from one response all come back, which is the ordinary session case. */
    @Test
    fun `multiple cookies survive the round trip`() {
        server.enqueue(
            MockResponse()
                .addHeader("Set-Cookie", "session=xyz; Path=/")
                .addHeader("Set-Cookie", "cf_clearance=abc123; Path=/")
        )
        server.enqueue(MockResponse())

        get("/login")
        server.takeRequest()
        get("/manga/1")

        val sent = server.takeRequest().getHeader("Cookie").orEmpty().split("; ").toSet()
        assertEquals(setOf("session=xyz", "cf_clearance=abc123"), sent)
    }

    /** Nothing stored for a host means no header at all, not an empty one. */
    @Test
    fun `a first request carries no cookie header`() {
        server.enqueue(MockResponse())

        get("/manga/1")

        assertNull(server.takeRequest().getHeader("Cookie"))
    }
}
