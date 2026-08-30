package app.otakureader.core.network.cookie

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The translation between OkHttp's cookie model and the two header formats `CookieManager` speaks.
 *
 * Driven through a fake store rather than Robolectric's `ShadowCookieManager`. The shadow keeps raw
 * strings and does not implement the manager's domain and path matching, so a test against it would
 * be asserting the shadow's behaviour, not Android's — and the defects that matter here are in this
 * class's parsing and formatting, which the fake exercises exactly.
 */
class WebViewCookieJarTest {

    private class FakeCookieStore : CookieStore {
        val written = mutableListOf<Pair<String, String>>()
        var header: String? = null

        override fun get(url: String): String? = header

        override fun set(url: String, value: String) {
            written += url to value
        }
    }

    private val store = FakeCookieStore()
    private val jar = WebViewCookieJar(store)
    private val url = "https://example.test/manga/1".toHttpUrl()

    /**
     * The full `Set-Cookie` form, not just `name=value`.
     *
     * Dropping the attributes would make every stored cookie a host-only session cookie: it would
     * not survive a restart, and it would not be sent to the subdomain its `Domain` names. The
     * Cloudflare clearance cookie is set for a domain and has an expiry, so both halves matter.
     */
    @Test
    fun `storing a cookie keeps its attributes`() {
        val cookie = Cookie.parse(url, "cf_clearance=abc123; Domain=example.test; Path=/; Max-Age=3600")!!

        jar.saveFromResponse(url, listOf(cookie))

        assertEquals(1, store.written.size)
        val (target, value) = store.written.single()
        assertEquals("https://example.test/manga/1", target)
        assertTrue("expected the Set-Cookie form, got: $value", value.startsWith("cf_clearance=abc123;"))
        assertTrue(value.contains("domain=example.test", ignoreCase = true))
        assertTrue(value.contains("path=/", ignoreCase = true))
    }

    @Test
    fun `every cookie in a response is stored`() {
        val cookies = listOf(
            Cookie.parse(url, "a=1")!!,
            Cookie.parse(url, "b=2")!!,
        )

        jar.saveFromResponse(url, cookies)

        assertEquals(listOf("a", "b"), store.written.map { it.second.substringBefore('=') })
    }

    /** `CookieManager` hands back one header holding every match, semicolon-separated. */
    @Test
    fun `loading splits the header into cookies`() {
        store.header = "session=xyz; cf_clearance=abc123"

        val loaded = jar.loadForRequest(url)

        assertEquals(listOf("session", "cf_clearance"), loaded.map { it.name })
        assertEquals(listOf("xyz", "abc123"), loaded.map { it.value })
    }

    /**
     * One unparseable entry must not take the request with it.
     *
     * The store is shared with every WebView in the app and with whatever a page's own JavaScript
     * wrote, so its contents are not this class's to guarantee. Failing the whole request over a
     * stray fragment would turn someone else's malformed cookie into "this source doesn't work".
     */
    @Test
    fun `an unparseable entry is skipped rather than failing the request`() {
        store.header = "good=1; ; =nonsense; alsogood=2"

        val loaded = jar.loadForRequest(url)

        assertEquals(listOf("good", "alsogood"), loaded.map { it.name })
    }

    @Test
    fun `no stored cookies means nothing is sent`() {
        store.header = null

        assertEquals(emptyList<Cookie>(), jar.loadForRequest(url))
    }

    /** An empty header is what the manager returns for a host it holds nothing for. */
    @Test
    fun `a blank header means nothing is sent`() {
        store.header = "   "

        assertEquals(emptyList<Cookie>(), jar.loadForRequest(url))
    }
}
