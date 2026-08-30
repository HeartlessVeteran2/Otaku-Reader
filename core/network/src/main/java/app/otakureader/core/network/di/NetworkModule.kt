package app.otakureader.core.network.di

import app.otakureader.core.common.network.PageImageHeaders
import app.otakureader.core.network.cloudflare.ChallengeUserAgentInterceptor
import app.otakureader.core.network.cloudflare.CloudflareInterceptor
import app.otakureader.core.network.BuildConfig
import app.otakureader.core.network.BytesEventListener
import app.otakureader.core.network.BytesRecorder
import app.otakureader.core.network.TrackerCertificatePinner
import app.otakureader.core.network.NetworkSettings
import app.otakureader.core.network.cookie.WebViewCookieJar
import app.otakureader.core.network.dns.PreferenceDns
import app.otakureader.core.network.interceptor.UserAgentInterceptor
import app.otakureader.core.network.interceptor.IgnoreGzipInterceptor
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for the OkHttpClient with tracker-endpoint certificate pinning. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TrackerOkHttp

/**
 * Qualifier for the page-image client, which attaches source-supplied request headers.
 *
 * Deliberately not the shared client: untrusted source code derives its own client from that
 * one and would inherit the header injection. See [NetworkModule.providePageImageOkHttpClient].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PageImageOkHttp

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Both of these read [NetworkSettings] on every call, which is what makes the Advanced-screen
     * settings apply to the next request rather than the next launch. The client itself is a
     * singleton built once, so anything captured by value here would need a restart to change.
     */
    @Provides
    @Singleton
    fun provideUserAgentInterceptor(settings: NetworkSettings): UserAgentInterceptor =
        UserAgentInterceptor { settings.userAgent }

    @Provides
    @Singleton
    fun providePreferenceDns(settings: NetworkSettings): PreferenceDns =
        PreferenceDns { settings.dohProvider }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        bytesRecorder: BytesRecorder,
        cloudflareInterceptor: CloudflareInterceptor,
        challengeUserAgentInterceptor: ChallengeUserAgentInterceptor,
        cookieJar: WebViewCookieJar,
        userAgentInterceptor: UserAgentInterceptor,
        dns: PreferenceDns,
        networkSettings: NetworkSettings,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // Without this, OkHttp's default is CookieJar.NO_COOKIES: nothing sent, nothing kept.
            // Every client in the app derives from this one, so that default silently disabled the
            // Cloudflare bypass everywhere except APK extensions — those alone had a jar, layered
            // on by NetworkHelper. See WebViewCookieJar for what that cost.
            //
            // Installed here rather than on each derived client so a session cookie set on one
            // path is visible on the others: a challenge solved while browsing has to carry into
            // the page-image client, or the chapter opens and its pages 403.
            .cookieJar(cookieJar)
            // On the SHARED client, unlike the page-image headers: a User-Agent is not a
            // per-source secret, and every backend needs the bypass — APK extensions through
            // NetworkHelper, JavaScript sources through JsHttpBridge, and the page-image client,
            // all of which derive from this one.
            //
            // An APPLICATION interceptor, because it must see the finished response and re-run
            // the whole call after the user solves the challenge. A network interceptor sees
            // one hop and cannot retry the request.
            .addInterceptor(cloudflareInterceptor)
            // Fills in the app's User-Agent where the caller chose none — otherwise OkHttp's
            // BridgeInterceptor sends "okhttp/4.12.0", which a number of sites reject and which
            // no setting could change. Added AFTER cloudflareInterceptor so the retry it issues
            // still passes through here.
            .addInterceptor(userAgentInterceptor)
            // Read per lookup, so switching provider in Settings applies to the next request
            // rather than the next launch. Defaults to the system resolver.
            .dns(dns)
            // A NETWORK interceptor, so each hop is stamped with the identity registered for
            // its own host. A redirect to a different host gets that host's User-Agent, or the
            // caller's own if it was never challenged.
            .addNetworkInterceptor(challengeUserAgentInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // readTimeout only bounds the gap between individual reads — a server trickling
            // one byte every 29 s never times out. callTimeout caps the whole call so a
            // stalled page download fails (and can be retried) instead of hanging forever.
            // 2 minutes is generous enough for large page images on slow connections.
            .callTimeout(2, TimeUnit.MINUTES)
            // IgnoreGzipInterceptor must come before BrotliInterceptor so it can strip the
            // transparent gzip header added by BridgeInterceptor, allowing BrotliInterceptor
            // to handle both gzip and Brotli decompression explicitly.
            .addNetworkInterceptor(IgnoreGzipInterceptor())
            .addNetworkInterceptor(BrotliInterceptor)
            // Record bytes per request category and network type for the Data Usage Dashboard.
            // BytesRecorder is a thin interface bound in :data to DataUsageRepository, avoiding
            // a cross-layer dependency from :core:network into :data.
            .eventListenerFactory(BytesEventListener.Factory { category, bytes ->
                bytesRecorder.record(category, bytes)
            })

        // Header logging: always on in debug, and in release only while the user has switched it
        // on from Advanced settings. The redaction list is the same either way, and is what makes
        // this safe to expose at all — a dump of headers otherwise puts tracker tokens and session
        // cookies into logcat, where any app holding READ_LOGS can read them.
        //
        // Installed behind a gate rather than by toggling the logger's `level`, so the decision is
        // made per call and the setting applies immediately instead of at the next launch. When it
        // is off, the gate proceeds without ever entering the logger: the cost is one volatile
        // read, and nothing is formatted.
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            redactHeader("X-Auth-Token")
        }
        builder.addInterceptor { chain ->
            if (BuildConfig.DEBUG || networkSettings.verboseLogging) {
                logging.intercept(chain)
            } else {
                chain.proceed(chain.request())
            }
        }

        return builder.build()
    }

    /**
     * Client for fetching page images — the reader's display path and the offline downloader.
     *
     * A **derived** client rather than the shared one, and that is a security boundary rather
     * than tidiness. `JsHttpBridge` builds its client from the shared one with `newBuilder()`,
     * so anything installed there is inherited by every request a JavaScript source makes.
     * Injecting page headers at that level would let a hostile source request another source's
     * registered image URL and have that source's credentials attached for it — routing straight
     * around the per-source scoping that keeps one source from reading another's stored login.
     * Deriving here instead means untrusted code never sees the interceptor.
     *
     * The derivation order also keeps injected headers out of the debug log. Application
     * interceptors run in the order added, and `HttpLoggingInterceptor` is already on the base
     * client, so it runs *before* this one and never observes what this adds — which matters
     * because the logger redacts a fixed list of header names and cannot know about a
     * source-supplied `X-Api-Key`.
     */
    @Provides
    @Singleton
    @PageImageOkHttp
    fun providePageImageOkHttpClient(
        okHttpClient: OkHttpClient,
        pageImageHeaders: PageImageHeaders,
    ): OkHttpClient =
        okHttpClient.newBuilder()
            // Attach what was recorded when the page list was fetched: a Referer naming the
            // source, plus anything the source supplied for that specific page. Both consumers
            // need it — installing it on the image loader alone left downloads hitting
            // hotlink-protected hosts without a Referer, so a chapter read fine and then failed
            // to save, with the 403 surfacing later as a broken offline page.
            //
            // A NETWORK interceptor, not an application one, and that distinction is the
            // security control. An application interceptor runs once, above OkHttp's redirect
            // handling, so whatever it adds is carried to every subsequent hop — and OkHttp
            // strips only `Authorization` on a host change, not a source-supplied `X-Api-Key`.
            // An image host that redirects cross-origin, whether compromised or simply
            // misconfigured, would then receive credentials meant for somewhere else, and the
            // source has no say in where its own CDN points.
            //
            // Running per hop makes that unreachable rather than merely mitigated: each hop is
            // looked up on its own URL, so a redirect to an unregistered host gets nothing and
            // one to a registered host gets exactly the headers belonging to it. Headers added
            // here do not propagate either, because OkHttp builds a redirect from the request
            // as it stood *above* the network interceptors.
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val extra = pageImageHeaders.headersFor(request.url.toString())
                if (extra.isEmpty()) {
                    chain.proceed(request)
                } else {
                    val withHeaders = request.newBuilder()
                    // Never overwrite a header the caller set deliberately.
                    extra.forEach { (name, value) ->
                        if (request.header(name) == null) withHeaders.header(name, value)
                    }
                    chain.proceed(withHeaders.build())
                }
            }
            .build()

    @Provides
    @Singleton
    @TrackerOkHttp
    fun provideTrackerOkHttpClient(okHttpClient: OkHttpClient): OkHttpClient =
        okHttpClient.newBuilder()
            .certificatePinner(TrackerCertificatePinner.build())
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://api.otakureader.app/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
