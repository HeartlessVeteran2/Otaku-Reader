package app.otakureader.data.tracker.mal

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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

/**
 * MyAnimeList tracker using OAuth 2.0 with PKCE.
 *
 * Public client id for open-source apps; no client secret is required when
 * using the PKCE extension (RFC 7636).
 *
 * Redirect URI must be registered in the MAL developer portal and match the
 * deep-link scheme declared in the app's AndroidManifest.xml:
 *   otakureader://mal-auth
 */
class MyAnimeListTracker @Inject constructor(
    private val preferences: AppPreferences,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : BaseTracker() {

    override val service: TrackService = TrackService.MAL

    private var codeVerifier: String = ""

    // ---- OAuth helpers -------------------------------------------------------

    /**
     * Generates a PKCE [codeVerifier] and builds the authorization URL.
     * The caller should open this URL in a browser or Custom Tab.
     */
    override fun getAuthorizationUrl(): String {
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        return "https://myanimelist.net/v1/oauth2/authorize" +
            "?response_type=code" +
            "&client_id=$CLIENT_ID" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=S256" +
            "&redirect_uri=$REDIRECT_URI"
    }

    /**
     * Exchanges the authorization [authCode] for access/refresh tokens and persists them.
     */
    override suspend fun login(authCode: String) {
        val formBody = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", authCode)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url("https://myanimelist.net/v1/oauth2/token")
            .post(formBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: return
        val tokenResponse = json.decodeFromString<MalTokenResponse>(body)

        preferences.setMalAccessToken(tokenResponse.accessToken)
        preferences.setMalRefreshToken(tokenResponse.refreshToken)
    }

    override suspend fun logout() {
        preferences.setMalAccessToken("")
        preferences.setMalRefreshToken("")
    }

    override suspend fun isLoggedIn(): Boolean =
        preferences.malAccessToken.first().isNotBlank()

    // ---- API calls -----------------------------------------------------------

    override suspend fun searchManga(title: String): List<TrackItem> {
        val accessToken = preferences.malAccessToken.first()
        if (accessToken.isBlank()) return emptyList()

        val url = "https://api.myanimelist.net/v2/manga" +
            "?q=${title.encodeUrl()}" +
            "&limit=10" +
            "&fields=id,title,main_picture,num_chapters"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        val searchResponse = json.decodeFromString<MalSearchResponse>(body)

        return searchResponse.data.map { node ->
            TrackItem(
                mangaId = 0,
                service = TrackService.MAL,
                remoteId = node.node.id,
                title = node.node.title,
                remoteUrl = "https://myanimelist.net/manga/${node.node.id}"
            )
        }
    }

    override suspend fun update(track: TrackItem) {
        val accessToken = preferences.malAccessToken.first()
        if (accessToken.isBlank()) return

        val malStatus = when (track.status) {
            TrackStatus.READING -> "reading"
            TrackStatus.COMPLETED -> "completed"
            TrackStatus.ON_HOLD -> "on_hold"
            TrackStatus.DROPPED -> "dropped"
            TrackStatus.PLAN_TO_READ -> "plan_to_read"
            TrackStatus.REPEATING -> "reading"
        }

        val formBody = FormBody.Builder()
            .add("status", malStatus)
            .add("num_chapters_read", track.lastChapterRead.toInt().toString())
            .apply { if (track.score > 0) add("score", track.score.toInt().toString()) }
            .build()

        val request = Request.Builder()
            .url("https://api.myanimelist.net/v2/manga/${track.remoteId}/my_list_status")
            .header("Authorization", "Bearer $accessToken")
            .patch(formBody)
            .build()

        okHttpClient.newCall(request).execute()
    }

    // ---- PKCE helpers --------------------------------------------------------

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, "UTF-8")

    // ---- Internal models -----------------------------------------------------

    @Serializable
    private data class MalTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String
    )

    @Serializable
    private data class MalSearchResponse(val data: List<MalNodeWrapper>)

    @Serializable
    private data class MalNodeWrapper(val node: MalNode)

    @Serializable
    private data class MalNode(val id: Long, val title: String)

    companion object {
        /**
         * Replace with your registered MAL client id from https://myanimelist.net/apiconfig.
         * For production builds, load this from a secure build config (e.g., BuildConfig field
         * populated from a local.properties file or a CI secret).
         */
        const val CLIENT_ID = "your_mal_client_id"
        const val REDIRECT_URI = "otakureader://mal-auth"
    }
}
