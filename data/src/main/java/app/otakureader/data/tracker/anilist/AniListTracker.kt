package app.otakureader.data.tracker.anilist

import app.otakureader.core.preferences.AppPreferences
import app.otakureader.data.tracker.BaseTracker
import app.otakureader.domain.model.TrackItem
import app.otakureader.domain.model.TrackService
import app.otakureader.domain.model.TrackStatus
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * AniList tracker using OAuth 2.0 implicit grant + GraphQL API.
 *
 * The access token is returned directly in the redirect URI fragment:
 *   otakureader://anilist-auth#access_token=TOKEN&token_type=Bearer&expires_in=SECONDS
 *
 * Because the token is in the fragment, [login] receives the raw token string
 * (parsed from the redirect by the caller) rather than an authorization code.
 */
class AniListTracker @Inject constructor(
    private val preferences: AppPreferences,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : BaseTracker() {

    override val service: TrackService = TrackService.ANILIST

    // ---- OAuth helpers -------------------------------------------------------

    /**
     * Returns the AniList OAuth authorization URL.
     * The caller opens this in a browser; the access token arrives in the redirect fragment.
     */
    override fun getAuthorizationUrl(): String =
        "https://anilist.co/api/v2/oauth/authorize" +
            "?client_id=$CLIENT_ID" +
            "&response_type=token" +
            "&redirect_uri=$REDIRECT_URI"

    /**
     * Stores the access [authCode] (which IS the bearer token for implicit grant).
     */
    override suspend fun login(authCode: String) {
        preferences.setAniListAccessToken(authCode)
    }

    override suspend fun logout() {
        preferences.setAniListAccessToken("")
    }

    override suspend fun isLoggedIn(): Boolean =
        preferences.aniListAccessToken.first().isNotBlank()

    // ---- GraphQL API calls ---------------------------------------------------

    override suspend fun searchManga(title: String): List<TrackItem> {
        val accessToken = preferences.aniListAccessToken.first()
        if (accessToken.isBlank()) return emptyList()

        val query = """
            query (${'$'}search: String) {
              Page(perPage: 10) {
                media(search: ${'$'}search, type: MANGA) {
                  id
                  title { userPreferred }
                  chapters
                  siteUrl
                }
              }
            }
        """.trimIndent()

        val variables = buildJsonObject { put("search", title) }
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val responseBody = response.body?.string() ?: return emptyList()
        val searchResponse = json.decodeFromString<AniListSearchResponse>(responseBody)

        return searchResponse.data.page.media.map { media ->
            TrackItem(
                mangaId = 0,
                service = TrackService.ANILIST,
                remoteId = media.id,
                title = media.title.userPreferred,
                totalChapters = media.chapters ?: 0,
                remoteUrl = media.siteUrl
            )
        }
    }

    override suspend fun update(track: TrackItem) {
        val accessToken = preferences.aniListAccessToken.first()
        if (accessToken.isBlank()) return

        val aniListStatus = when (track.status) {
            TrackStatus.READING -> "CURRENT"
            TrackStatus.COMPLETED -> "COMPLETED"
            TrackStatus.ON_HOLD -> "PAUSED"
            TrackStatus.DROPPED -> "DROPPED"
            TrackStatus.PLAN_TO_READ -> "PLANNING"
            TrackStatus.REPEATING -> "REPEATING"
        }

        val mutation = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}score: Float) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status, score: ${'$'}score) {
                id
              }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("mediaId", track.remoteId.toInt())
            put("progress", track.lastChapterRead.toInt())
            put("status", aniListStatus)
            put("score", track.score)
        }

        val payload = buildJsonObject {
            put("query", mutation)
            put("variables", variables)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()

        okHttpClient.newCall(request).execute()
    }

    // ---- Internal models -----------------------------------------------------

    @Serializable
    private data class AniListSearchResponse(val data: AniListData)

    @Serializable
    private data class AniListData(
        @SerialName("Page") val page: AniListPage
    )

    @Serializable
    private data class AniListPage(val media: List<AniListMedia>)

    @Serializable
    private data class AniListMedia(
        val id: Long,
        val title: AniListTitle,
        val chapters: Int? = null,
        val siteUrl: String = ""
    )

    @Serializable
    private data class AniListTitle(val userPreferred: String)

    companion object {
        /**
         * Replace with your registered AniList client id from https://anilist.co/settings/developer.
         * For production builds, load this from a secure build config (e.g., BuildConfig field
         * populated from a local.properties file or a CI secret).
         */
        const val CLIENT_ID = "your_anilist_client_id"
        const val REDIRECT_URI = "otakureader://anilist-auth"
        private const val GRAPHQL_URL = "https://graphql.anilist.co"
    }
}
