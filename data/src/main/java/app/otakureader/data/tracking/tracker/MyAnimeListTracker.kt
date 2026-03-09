package app.otakureader.data.tracking.tracker

import app.otakureader.data.tracking.api.MalListStatus
import app.otakureader.data.tracking.api.MyAnimeListApi
import app.otakureader.data.tracking.api.MyAnimeListOAuthApi
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.tracking.Tracker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracker implementation for [MyAnimeList](https://myanimelist.net/).
 *
 * Authentication uses the MAL OAuth 2.0 PKCE flow.
 * As with [ShikimoriTracker], the authorization code is passed as [password].
 *
 * MAL status strings map as follows:
 *  - "reading"      → READING
 *  - "completed"    → COMPLETED
 *  - "on_hold"      → ON_HOLD
 *  - "dropped"      → DROPPED
 *  - "plan_to_read" → PLAN_TO_READ
 */
class MyAnimeListTracker(
    private val oauthApi: MyAnimeListOAuthApi,
    private val api: MyAnimeListApi,
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String
) : Tracker {

    override val id: Int = TrackerType.MY_ANIME_LIST
    override val name: String = "MyAnimeList"

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private val tokenMutex = Mutex()

    override val isLoggedIn: Boolean
        get() = accessToken != null

    /**
     * @param username the PKCE code verifier
     * @param password the authorization code obtained from the OAuth redirect
     */
    override suspend fun login(username: String, password: String): Boolean {
        return try {
            val response = oauthApi.getAccessToken(
                clientId = clientId,
                clientSecret = clientSecret,
                code = password,
                codeVerifier = username,
                redirectUri = redirectUri
            )
            tokenMutex.withLock {
                accessToken = response.accessToken
                refreshToken = response.refreshToken
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun logout() {
        accessToken = null
        refreshToken = null
    }

    override suspend fun search(query: String): List<TrackEntry> {
        return api.searchManga(query = query).data.map { item ->
            val manga = item.node
            TrackEntry(
                remoteId = manga.id,
                mangaId = 0L,
                trackerId = id,
                title = manga.title,
                remoteUrl = "https://myanimelist.net/manga/${manga.id}",
                totalChapters = manga.numChapters
            )
        }
    }

    override suspend fun find(remoteId: Long): TrackEntry? {
        return try {
            val manga = api.getManga(remoteId)
            val listStatus = manga.listStatus ?: return null
            TrackEntry(
                remoteId = remoteId,
                mangaId = 0L,
                trackerId = id,
                title = manga.title,
                remoteUrl = "https://myanimelist.net/manga/$remoteId",
                status = statusFromMal(listStatus.status),
                lastChapterRead = listStatus.numChaptersRead.toFloat(),
                totalChapters = manga.numChapters,
                score = listStatus.score.toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun update(entry: TrackEntry): TrackEntry {
        return try {
            api.updateListStatus(
                id = entry.remoteId,
                status = statusToMal(entry.status),
                chaptersRead = entry.lastChapterRead.toInt(),
                score = entry.score.toInt()
            )
            entry
        } catch (e: Exception) {
            entry
        }
    }

    override fun toTrackStatus(remoteStatus: Int): TrackStatus = TrackStatus.fromOrdinal(remoteStatus)

    override fun toRemoteStatus(status: TrackStatus): Int = status.ordinal

    private fun statusFromMal(malStatus: String): TrackStatus = when (malStatus) {
        "reading" -> TrackStatus.READING
        "completed" -> TrackStatus.COMPLETED
        "on_hold" -> TrackStatus.ON_HOLD
        "dropped" -> TrackStatus.DROPPED
        "plan_to_read" -> TrackStatus.PLAN_TO_READ
        else -> TrackStatus.PLAN_TO_READ
    }

    private fun statusToMal(status: TrackStatus): String = when (status) {
        TrackStatus.READING -> "reading"
        TrackStatus.COMPLETED -> "completed"
        TrackStatus.ON_HOLD -> "on_hold"
        TrackStatus.DROPPED -> "dropped"
        TrackStatus.PLAN_TO_READ -> "plan_to_read"
        TrackStatus.RE_READING -> "reading"
    }
}
