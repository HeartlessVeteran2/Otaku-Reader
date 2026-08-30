package app.otakureader.core.network.cookie

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shared client's cookie jar, backed by the same `CookieManager` every WebView uses.
 *
 * ## Why this exists
 *
 * The shared `OkHttpClient` had no jar at all, so OkHttp used its default — `CookieJar.NO_COOKIES`,
 * which sends nothing and stores nothing. Three separate KDocs in this repository asserted a jar
 * was there: `CloudflareInterceptor` ("the retry carries the clearance cookie automatically,
 * because the cookie jar and the WebView share Android's `CookieManager`"), `WebViewChallengeManager`
 * ("the app's cookie jar is backed by the same Android `CookieManager` the WebView writes to") and
 * `JsHttpBridge` ("every interceptor, the cookie jar, the certificate pinner..."). Only APK
 * extensions actually had one, because `NetworkHelper` layers its own `AndroidCookieJar` on top of
 * the shared client for its own use.
 *
 * Everything else therefore ran without cookies, and the Cloudflare bypass — the feature those
 * comments describe — silently did nothing on those paths:
 *
 * - **JavaScript sources.** `JsHttpBridge` derives its client from the shared one, so a source
 *   whose challenge the user had just solved in the WebView was challenged again on every request.
 * - **Page images**, in the reader and the downloader alike. Those go through the page-image
 *   client, also derived from the shared one — so even an APK source whose chapter list loaded
 *   fine through `NetworkHelper` could fail to load a single page behind Cloudflare.
 * - **OPDS**, whose Komga/Kavita session cookie was discarded on the response that set it.
 *
 * ## The translation
 *
 * `CookieManager` speaks the two header formats rather than OkHttp's [Cookie] type, so this class
 * is a translator in both directions:
 *
 * - **Storing** writes each cookie's full `Set-Cookie` form, which is what `Cookie.toString()`
 *   produces — attributes included, so domain, path and expiry survive and `CookieManager` can
 *   apply its own matching rules on the way back out.
 * - **Loading** parses the `name=value; name2=value2` header `CookieManager` returns. That header
 *   carries no attributes, which is not a loss: the manager has already decided these cookies match
 *   this URL, so parsing each against the request URL yields exactly what should be sent.
 *
 * A fragment that will not parse is skipped rather than failing the request. `Cookie.parse` returns
 * null for a malformed pair, and one bad entry written by some other WebView user must not take the
 * whole request with it.
 */
@Singleton
class WebViewCookieJar internal constructor(
    private val store: CookieStore,
) : CookieJar {

    @Inject
    constructor() : this(AndroidWebViewCookieStore())

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val target = url.toString()
        cookies.forEach { store.set(target, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = store.get(url.toString())?.takeIf { it.isNotBlank() } ?: return emptyList()
        return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    /**
     * Discards every stored cookie, for the Advanced screen's "Clear cookies".
     *
     * This clears the shared `CookieManager`, so it takes WebView's cookies with it — which is the
     * point rather than a side effect. A source stuck in a challenge loop is usually holding a
     * clearance cookie the site no longer accepts, and clearing only OkHttp's half would leave the
     * WebView to hand the same dead cookie straight back.
     *
     * It also signs the user out of anything they had logged into through a source's WebView, so
     * the screen asks first.
     *
     * Suspends until the removal has actually happened — Android's own call is asynchronous, and
     * returning early would let the caller report success while the old cookies were still being
     * sent.
     */
    suspend fun clear() = store.clear()
}
