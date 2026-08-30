package app.otakureader.core.network.cookie

import android.webkit.CookieManager

/**
 * The two operations [WebViewCookieJar] needs from a cookie store, as a seam.
 *
 * It exists for two reasons, not just testing. Android's `CookieManager` is a WebView class, so a
 * JVM unit test cannot exercise the jar against it — and a device with no WebView installed cannot
 * either, which is the second reason: [AndroidWebViewCookieStore] resolves the manager lazily and
 * degrades to holding nothing rather than throwing on every request the app makes.
 */
internal interface CookieStore {
    /** The `name=value; name2=value2` header for [url], or null when there is nothing to send. */
    fun get(url: String): String?

    /** Stores one `Set-Cookie`-shaped [value] against [url]. */
    fun set(url: String, value: String)
}

/**
 * The real store: Android's shared [CookieManager], the same one every WebView reads and writes.
 *
 * Sharing it is the entire point. A Cloudflare challenge is solved in a WebView, and the clearance
 * cookie it hands back is written here — so a jar backed by anything else would leave that cookie
 * where OkHttp cannot see it, and the retry would be challenged again.
 *
 * The manager is resolved lazily and defensively. `CookieManager.getInstance()` initialises WebView
 * on first call and throws when the system has no WebView provider (an unusual device, but a real
 * one). Throwing from a `CookieJar` would fail every single HTTP call the app makes, which is a far
 * worse outcome than the no-cookie behaviour that preceded this class — so that is what it falls
 * back to.
 */
internal class AndroidWebViewCookieStore : CookieStore {

    private val manager: CookieManager? by lazy {
        runCatching { CookieManager.getInstance() }.getOrNull()
    }

    override fun get(url: String): String? = runCatching { manager?.getCookie(url) }.getOrNull()

    override fun set(url: String, value: String) {
        runCatching { manager?.setCookie(url, value) }
    }
}
