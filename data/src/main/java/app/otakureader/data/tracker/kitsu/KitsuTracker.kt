package app.otakureader.data.tracker.kitsu

import app.otakureader.core.preferences.AppPreferences
import app.otakureader.data.tracker.BaseTracker
import app.otakureader.domain.model.TrackItem
import app.otakureader.domain.model.TrackService
import app.otakureader.domain.model.TrackStatus
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * Kitsu tracker using OAuth 2.0 Resource Owner Password Credentials grant.
 *
 * [login] expects the password-grant response's access token directly.
 * To perform the full password login flow (email + password → token), call
 * [loginWithCredentials] instead from the UI.
 */
class KitsuTracker @Inject constructor(
    private val preferences: AppPreferences,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : BaseTracker() {

    override val service: TrackService = TrackService.KITSU

    // ---- OAuth helpers -------------------------------------------------------

    /** Kitsu uses password grant, so there is no browser-based authorization URL. */
    override fun getAuthorizationUrl(): String = ""

    /**
     * Stores the [authCode] as the Kitsu access token (used after password-grant exchange).
     */
    override suspend fun login(authCode: String) {
        preferences.setKitsuAccessToken(authCode)
    }

    override suspend fun logout() {
        preferences.setKitsuAccessToken("")
        preferences.setKitsuRefreshToken("")
    }

    override suspend fun isLoggedIn(): Boolean =
        preferences.kitsuAccessToken.first().isNotBlank()

    /**
     * Authenticates using Kitsu's password grant.
     * Stores the resulting access and refresh tokens in preferences.
     *
     * @param email Kitsu account email.
     * @param password Kitsu account password.
     * @return true on success, false on failure.
     */
    suspend fun loginWithCredentials(email: String, password: String): Boolean {
        val formBody = FormBody.Builder()
            .add("grant_type", "password")
            .add("username", email)
            .add("password", password)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .build()

        val request = Request.Builder()
            .url("https://kitsu.io/api/oauth/token")
            .post(formBody)
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return false
            val body = response.body?.string() ?: return false
            val tokenResponse = json.decodeFromString<KitsuTokenResponse>(body)
            preferences.setKitsuAccessToken(tokenResponse.accessToken)
            preferences.setKitsuRefreshToken(tokenResponse.refreshToken)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- API calls -----------------------------------------------------------

    override suspend fun searchManga(title: String): List<TrackItem> {
        val accessToken = preferences.kitsuAccessToken.first()
        if (accessToken.isBlank()) return emptyList()

        val url = "https://kitsu.io/api/edge/manga" +
            "?filter[text]=${title.encodeUrl()}" +
            "&page[limit]=10" +
            "&fields[manga]=id,canonicalTitle,chapterCount,slug"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/vnd.api+json")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        val searchResponse = json.decodeFromString<KitsuSearchResponse>(body)

        return searchResponse.data.map { item ->
            val kitsuId = item.id.toLongOrNull() ?: 0L
            TrackItem(
                mangaId = 0,
                service = TrackService.KITSU,
                remoteId = kitsuId,
                title = item.attributes.canonicalTitle,
                totalChapters = item.attributes.chapterCount ?: 0,
                remoteUrl = "https://kitsu.io/manga/${item.attributes.slug}"
            )
        }
    }

    override suspend fun update(track: TrackItem) {
        val accessToken = preferences.kitsuAccessToken.first()
        if (accessToken.isBlank()) return

        val kitsuStatus = when (track.status) {
            TrackStatus.READING -> "current"
            TrackStatus.COMPLETED -> "completed"
            TrackStatus.ON_HOLD -> "on_hold"
            TrackStatus.DROPPED -> "dropped"
            TrackStatus.PLAN_TO_READ -> "planned"
            TrackStatus.REPEATING -> "current"
        }

        val payload = """
            {
              "data": {
                "type": "libraryEntries",
                "attributes": {
                  "status": "$kitsuStatus",
                  "progress": ${track.lastChapterRead.toInt()},
                  "ratingTwenty": ${if (track.score > 0) (track.score * 2).toInt() else "null"}
                }
              }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://kitsu.io/api/edge/library-entries/${track.remoteId}")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/vnd.api+json")
            .header("Content-Type", "application/vnd.api+json")
            .patch(payload.toRequestBody("application/vnd.api+json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute()
    }

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, "UTF-8")

    // ---- Internal models -----------------------------------------------------

    @Serializable
    private data class KitsuTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String
    )

    @Serializable
    private data class KitsuSearchResponse(val data: List<KitsuItem>)

    @Serializable
    private data class KitsuItem(val id: String, val attributes: KitsuAttributes)

    @Serializable
    private data class KitsuAttributes(
        val canonicalTitle: String,
        val chapterCount: Int? = null,
        val slug: String = ""
    )

    companion object {
        /** Public Kitsu client id for installed apps. */
        const val CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7be3ab46bf1299f05ecd856bdb7dd"
        const val CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"
    }
}
