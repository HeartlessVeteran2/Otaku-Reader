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
        pageImageHeaders: PageImageHeaders,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // Attach the headers recorded when a page list was fetched — a Referer naming the
            // source, plus anything the source supplied for that specific page.
            //
            // This belongs on the SHARED client, not on the image loader. Page images are
            // fetched by two entirely separate consumers: Coil, for display, and Downloader,
            // for saving chapters offline. Installing it on Coil alone leaves downloads
            // hitting hotlink-protected hosts without a Referer, so a chapter reads fine and
            // then fails to download — with the 403 surfacing much later as a broken saved
            // page. Here, every consumer of this client is covered, including ones not yet
            // written.
            //
            // Not gated on RequestCategory: Coil sets its tag through newBuilder(), which
            // appends its interceptor *after* these, so the tag is not yet present when this
            // runs. Scoping instead comes from the registry itself, which only ever holds
            // hosts that served a page list — a request to any other host finds nothing and is
            // left untouched.
            .addInterceptor { chain ->
                val request = chain.request()
                val extra = pageImageHeaders.headersFor(request.url.toString())
                if (extra.isEmpty()) {
                    chain.proceed(request)
                } else {
                    val builder = request.newBuilder()
                    // Never overwrite a header the caller set deliberately.
                    extra.forEach { (name, value) ->
                        if (request.header(name) == null) builder.header(name, value)
                    }
                    chain.proceed(builder.build())
                }
            }
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
