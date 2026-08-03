package app.otakureader.data.tracking.tracker

import app.otakureader.core.preferences.TrackerTokenStore
import app.otakureader.data.tracking.api.AniListApi
import app.otakureader.data.tracking.api.AniListGraphQlQuery
import app.otakureader.data.tracking.di.TrackerCredentials
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.tracking.Tracker
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Tracker implementation for [AniList](https://anilist.co/).
 *
 * AniList uses OAuth 2.0 (implicit grant / authorization-code) combined with
 * a GraphQL API.  The authorization code is passed as [password] in [login].
 *
 * AniList status strings map as follows:
 *  - "CURRENT"   → READING
 *  - "COMPLETED" → COMPLETED
 *  - "PAUSED"    → ON_HOLD
 *  - "DROPPED"   → DROPPED
 *  - "PLANNING"  → PLAN_TO_READ
 *  - "REPEATING" → RE_READING
 */
class AniListTracker(
    private val api: AniListApi,
    private val tokenStore: TrackerTokenStore,
    /**
     * Defaults to the build-injected credential; a parameter so tests can supply one.
     *
     * Reading `TrackerCredentials.ANILIST_CLIENT_ID` inside the function made both outcomes
     * untestable in any single build: with an id injected only the configured branch could run,
     * without one only the unconfigured branch, and the skipped half reported as a pass either
     * way. A parameter removes the ambient build state from the question entirely.
     */
    private val clientId: String = TrackerCredentials.ANILIST_CLIENT_ID,
) : Tracker {

    override val id: Int = TrackerType.ANILIST
    override val name: String = "AniList"

    private var accessToken: String? = tokenStore.getTokens(TrackerType.ANILIST)?.accessToken
    private var currentUserId: Long? = tokenStore.getTokens(TrackerType.ANILIST)?.userId

    override val isLoggedIn: Boolean
        get() = accessToken != null

    /**
     * The URL to open for login.
     *
     * Without this override the base [Tracker] default returns null and `TrackingViewModel`
     * falls back to a bare `https://anilist.co/api/v2/oauth/authorize` — no `client_id`, no
     * `redirect_uri`, no `response_type` — which AniList rejects outright. Login could not
     * complete at all.
     *
     * `response_type=token` is the **implicit** grant, matching what [login] already expects:
     * it takes a bearer token directly and performs no code-for-token exchange. The
     * authorization-code flow would need a client secret, and a secret shipped inside an APK
     * is not a secret.
     *
     * [codeVerifier] is unused. It exists on the interface for the PKCE trackers (Kitsu, MAL);
     * the implicit grant has no code to protect, so there is nothing to bind it to. Accepting
     * and ignoring it is honest here — inventing a use would imply a protection that is not
     * present.
     *
     * [state] is emphatically *not* unused. The implicit grant hands the access token straight
     * to the redirect URI, so without a state the app would accept any `app.otakureader://
     * anilist-oauth#access_token=…` link anyone can get the device to open — an attacker-chosen
     * account silently substituted for the user's. The state is the only thing tying the token
     * that comes back to the login this app started.
     */
    @Suppress("UnusedParameter")
    override fun authorizationUrl(codeVerifier: String, state: String): String? {
        // Blank means the build had no ANILIST_CLIENT_ID. Null is the interface's "this tracker
        // has no authorization URL", and the caller now treats that as unconfigured rather than
        // substituting a bare endpoint — see TrackingViewModel.initiateLogin.
        if (clientId.isBlank()) return null
        return "https://anilist.co/api/v2/oauth/authorize" +
            "?client_id=$clientId" +
            "&redirect_uri=${TrackerCredentials.ANILIST_REDIRECT_URI}" +
            "&response_type=token" +
            // Percent-encoded rather than interpolated raw. Today's caller generates a UUID, which
            // needs no encoding, so this changes nothing now — but the callback compares the
            // returned value against the stored one for equality, and a state that ever grew a `&`
            // would split into two parameters and fail that comparison forever. DeepLinkHandler
            // decodes with the matching URLDecoder, so the pair round-trips.
            "&state=${URLEncoder.encode(state, StandardCharsets.UTF_8.name())}"
    }

    /** @param password the OAuth bearer token obtained from the AniList implicit flow. */
    override suspend fun login(username: String, password: String): Boolean {
        return try {
            accessToken = password
            tokenStore.saveTokens(trackerId = id, accessToken = password)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            accessToken = null
            false
        }
    }

    override fun logout() {
        accessToken = null
        currentUserId = null
        tokenStore.clearTokens(id)
    }

    override suspend fun search(query: String): List<TrackEntry> {
        val gqlQuery = """
            query (${'$'}search: String) {
              Page { media(search: ${'$'}search, type: MANGA) {
                id title { romaji english } chapters coverImage { large }
              } }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("search", query) }
        val response = api.query(AniListGraphQlQuery(gqlQuery, variables))
        return response.data?.page?.media.orEmpty().map { media ->
            TrackEntry(
                remoteId = media.id,
                mangaId = 0L,
                trackerId = id,
                title = media.title?.english ?: media.title?.romaji ?: "",
                remoteUrl = "https://anilist.co/manga/${media.id}",
                totalChapters = media.chapters ?: 0
            )
        }
    }

    override suspend fun find(remoteId: Long): TrackEntry? {
        val gqlQuery = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: MANGA) {
                id title { romaji english } chapters mediaListEntry { id status score progress }
              }
            }
        """.trimIndent()
        // Int, not a quoted string: the query declares `${'$'}id: Int`.
        val variables = buildJsonObject { put("id", remoteId) }
        return try {
            val response = api.query(AniListGraphQlQuery(gqlQuery, variables))
            val media = response.data?.media ?: return null
            val listEntry = media.mediaListEntry ?: return null
            TrackEntry(
                remoteId = remoteId,
                mangaId = 0L,
                trackerId = id,
                title = media.title?.english ?: media.title?.romaji ?: "",
                remoteUrl = "https://anilist.co/manga/$remoteId",
                status = statusFromAniList(listEntry.status),
                lastChapterRead = listEntry.progress.toFloat(),
                totalChapters = media.chapters ?: 0,
                score = listEntry.score
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun update(entry: TrackEntry): TrackEntry {
        val gqlMutation = """
            mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}score: Float, ${'$'}progress: Int) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status, scoreRaw: ${'$'}score, progress: ${'$'}progress) {
                id status score progress
              }
            }
        """.trimIndent()
        // Each value keeps the type the mutation declares. Sending these as strings is what
        // made every update fail server-side while looking successful here.
        val variables = buildJsonObject {
            put("mediaId", entry.remoteId)
            put("status", statusToAniList(entry.status))
            put("score", entry.score)
            put("progress", entry.lastChapterRead.toInt())
        }
        return try {
            api.query(AniListGraphQlQuery(gqlMutation, variables))
            entry
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            entry
        }
    }

    override fun toTrackStatus(remoteStatus: Int): TrackStatus = TrackStatus.fromOrdinal(remoteStatus)

    override fun toRemoteStatus(status: TrackStatus): Int = status.ordinal

    private fun statusFromAniList(aniListStatus: String): TrackStatus = when (aniListStatus) {
        "CURRENT" -> TrackStatus.READING
        "COMPLETED" -> TrackStatus.COMPLETED
        "PAUSED" -> TrackStatus.ON_HOLD
        "DROPPED" -> TrackStatus.DROPPED
        "PLANNING" -> TrackStatus.PLAN_TO_READ
        "REPEATING" -> TrackStatus.RE_READING
        else -> TrackStatus.PLAN_TO_READ
    }

    private fun statusToAniList(status: TrackStatus): String = when (status) {
        TrackStatus.READING -> "CURRENT"
        TrackStatus.COMPLETED -> "COMPLETED"
        TrackStatus.ON_HOLD -> "PAUSED"
        TrackStatus.DROPPED -> "DROPPED"
        TrackStatus.PLAN_TO_READ -> "PLANNING"
        TrackStatus.RE_READING -> "REPEATING"
    }
}
