package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Host-supplied network service for loaded Tachiyomi extensions.
 *
 * This is a SIMPLIFIED port of Tachiyomi/Komikku's NetworkHelper. It deliberately drops:
 * - DoH provider selection (NetworkPreferences/DohProviders)
 * - The WebView-based CloudflareInterceptor (no WebView dependency here)
 * - The resumable big-file downloader
 *
 * Extensions resolve this via `Injekt.get<NetworkHelper>()` inside [HttpSource]. They reference
 * [client], [cloudflareClient], [cookieJar] and [defaultUserAgentProvider]; those are the only
 * members that form the binary contract.
 *
 * The OkHttp client is built on top of an optional [baseClient] (the host's shared
 * `OkHttpClient` from core/network) so extension traffic reuses the host's connection pool and
 * interceptor chain, with an [AndroidCookieJar] and a per-source disk cache layered on top.
 */
open class NetworkHelper(
    private val context: Context,
    private val userAgent: String = DEFAULT_USER_AGENT,
    baseClient: OkHttpClient? = null,
    /**
     * Reads the User-Agent the user set in Advanced settings, or null to use [userAgent].
     *
     * A function rather than a value because [defaultUserAgentProvider] is called per request and
     * the setting can change while the app runs; capturing a string here would mean the override
     * only reached extensions after a restart.
     */
    private val userAgentOverride: (() -> String?)? = null,
) {

    open val cookieJar = AndroidCookieJar()

    private val builder: OkHttpClient.Builder = (baseClient?.newBuilder() ?: OkHttpClient.Builder())
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .cache(
            Cache(
                directory = File(context.cacheDir, "network_cache"),
                maxSize = 5L * 1024 * 1024, // 5 MiB
            ),
        )

    open val client: OkHttpClient by lazy { builder.build() }

    /**
     * @deprecated Since extension-lib 1.5 — the regular client handles Cloudflare by default.
     * Kept for API parity with extensions that still reference it.
     */
    @Deprecated("The regular client handles Cloudflare by default", ReplaceWith("client"))
    open val cloudflareClient: OkHttpClient
        get() = client

    /**
     * The identity extensions send. Every `HttpSource` builds its headers from this, which is how
     * the Advanced-settings override reaches APK extensions — they never pass through the shared
     * client's `UserAgentInterceptor`, because they set the header themselves.
     */
    fun defaultUserAgentProvider(): String =
        userAgentOverride?.invoke()?.takeIf { it.isNotBlank() } ?: userAgent

    companion object {
        /**
         * Kept as a `const` because it is part of the surface loaded extensions compile against.
         * The value the app actually sends lives in `NetworkSettings.DEFAULT_USER_AGENT`, which
         * this must match; `NetworkHelperUserAgentTest` pins them together so a change to one
         * cannot silently diverge from the other.
         */
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
