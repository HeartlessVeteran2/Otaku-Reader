package app.otakureader.core.network.di

import app.otakureader.core.network.BuildConfig
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
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // IgnoreGzipInterceptor must come before BrotliInterceptor so it can strip the
            // transparent gzip header added by BridgeInterceptor, allowing BrotliInterceptor
            // to handle both gzip and Brotli decompression explicitly.
            .addNetworkInterceptor(IgnoreGzipInterceptor())
            .addNetworkInterceptor(BrotliInterceptor)

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
