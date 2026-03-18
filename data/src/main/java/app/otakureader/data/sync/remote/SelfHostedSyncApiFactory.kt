package app.otakureader.data.sync.remote

import app.otakureader.core.preferences.SyncPreferences
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating SelfHostedSyncApi instances with dynamic base URLs.
 */
@Singleton
class SelfHostedSyncApiFactory @Inject constructor(
    private val syncPreferences: SyncPreferences,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    // Cache Retrofit instances by base URL to avoid rebuilding
    private val retrofitCache = mutableMapOf<String, Retrofit>()

    suspend fun create(): SelfHostedSyncApi {
        val baseUrl = syncPreferences.getSelfHostedServerUrl().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Server URL not configured")

        // Validate URL has scheme
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw IllegalArgumentException("Server URL must start with http:// or https://")
        }

        // Ensure URL ends with /
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        // Get or create cached Retrofit instance
        val retrofit = retrofitCache.getOrPut(normalizedUrl) {
            Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        }

        return retrofit.create(SelfHostedSyncApi::class.java)
    }

    /**
     * Creates an API instance with the current URL from preferences,
     * or returns null if URL is not configured or invalid.
     */
    suspend fun createOrNull(): SelfHostedSyncApi? {
        return try {
            create()
        } catch (e: IllegalStateException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
