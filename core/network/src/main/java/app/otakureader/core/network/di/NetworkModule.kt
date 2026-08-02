package app.otakureader.core.network.di

import app.otakureader.core.common.network.PageImageHeaders
import app.otakureader.core.network.BuildConfig
import app.otakureader.core.network.BytesEventListener
import app.otakureader.core.network.BytesRecorder
import app.otakureader.core.network.TrackerCertificatePinner
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

    @Provides
    @Singleton
    fun provideOkHttpClient(
        bytesRecorder: BytesRecorder,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
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

        // Enable HTTP logging only in debug builds; redact sensitive headers to prevent
        // token exposure in logcat even when a debug APK reaches a non-developer device.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                    redactHeader("Authorization")
                    redactHeader("Cookie")
                    redactHeader("Set-Cookie")
                    redactHeader("X-Auth-Token")
                }
            )
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
            .addInterceptor { chain ->
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
