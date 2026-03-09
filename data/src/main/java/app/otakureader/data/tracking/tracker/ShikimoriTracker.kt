package app.otakureader.data.tracking.tracker

import app.otakureader.data.tracking.api.ShikimoriApi
import app.otakureader.data.tracking.api.ShikimoriOAuthApi
import app.otakureader.data.tracking.api.ShikimoriUserRateBody
import app.otakureader.data.tracking.api.ShikimoriUserRateRequest
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.tracking.Tracker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracker implementation for [Shikimori](https://shikimori.one/).
 *
 * Authentication uses the Shikimori OAuth 2.0 authorization-code flow.
 * Because the authorization-code flow requires a browser redirect, the
 * [login] method here accepts a pre-obtained authorization code as the
 * [password] parameter (the [username] parameter is unused).
 *
 * Shikimori status strings map as follows:
 *  - "watching"  → READING
 *  - "completed" → COMPLETED
 *  - "on_hold"   → ON_HOLD
 *  - "dropped"   → DROPPED
 *  - "planned"   → PLAN_TO_READ
 *  - "rewatching"→ RE_READING
 */
class ShikimoriTracker(
    private val oauthApi: ShikimoriOAuthApi,
    private val api: ShikimoriApi,
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String
) : Tracker {

    override val id: Int = TrackerType.SHIKIMORI
    override val name: String = "Shikimori"

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var currentUserId: Long? = null
    private val tokenMutex = Mutex()

    override val isLoggedIn: Boolean
        get() = accessToken != null

    /**
     * @param username unused (Shikimori uses OAuth2 code flow)
     * @param password the authorization code obtained from the OAuth redirect
     */
    override suspend fun login(username: String, password: String): Boolean {
        return try {
            val response = oauthApi.getAccessToken(
                clientId = clientId,
                clientSecret = clientSecret,
                code = password,
                redirectUri = redirectUri
            )
            tokenMutex.withLock {
                accessToken = response.accessToken
                refreshToken = response.refreshToken
            }
            val uid = try {
                api.getCurrentUser().id
            } catch (e: Exception) {
                null
            }
            if (uid != null) {
                currentUserId = uid
                true
            } else {
                tokenMutex.withLock {
                    accessToken = null
                    refreshToken = null
                }
                false
            }
        } catch (e: Exception) {
            tokenMutex.withLock {
                accessToken = null
                refreshToken = null
            }
            false
        }
    }

    override fun logout() {
        accessToken = null
        refreshToken = null
        currentUserId = null
    }

    override suspend fun search(query: String): List<TrackEntry> {
        return api.searchManga(query = query).map { manga ->
            TrackEntry(
                remoteId = manga.id,
                mangaId = 0L,
                trackerId = id,
                title = manga.name,
                remoteUrl = "https://shikimori.one${manga.url}",
                totalChapters = manga.chapters
            )
        }
    }

    override suspend fun find(remoteId: Long): TrackEntry? {
        val uid = currentUserId ?: return null
        return try {
            val rates = api.getUserRate(userId = uid, targetId = remoteId)
            val rate = rates.firstOrNull() ?: return null
            val manga = api.getManga(remoteId)
            TrackEntry(
                remoteId = remoteId,
                mangaId = 0L,
                trackerId = id,
                title = manga.name,
                remoteUrl = "https://shikimori.one${manga.url}",
                status = statusFromShikimori(rate.status),
                lastChapterRead = rate.chapters.toFloat(),
                totalChapters = manga.chapters,
                score = rate.score.toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun update(entry: TrackEntry): TrackEntry {
        val uid = currentUserId ?: return entry
        val body = ShikimoriUserRateBody(
            userId = uid,
            targetId = entry.remoteId,
            targetType = "Manga",
            status = statusToShikimori(entry.status),
            score = entry.score.toInt(),
            chapters = entry.lastChapterRead.toInt()
        )
        val request = ShikimoriUserRateRequest(userRate = body)
        return try {
            val existing = api.getUserRate(userId = uid, targetId = entry.remoteId).firstOrNull()
            if (existing == null) {
                api.createUserRate(request)
            } else {
                api.updateUserRate(existing.id, request)
            }
            entry
        } catch (e: Exception) {
            entry
        }
    }

    override fun toTrackStatus(remoteStatus: Int): TrackStatus = TrackStatus.fromOrdinal(remoteStatus)

    override fun toRemoteStatus(status: TrackStatus): Int = status.ordinal

    private fun statusFromShikimori(shikimoriStatus: String): TrackStatus = when (shikimoriStatus) {
        "watching" -> TrackStatus.READING
        "completed" -> TrackStatus.COMPLETED
        "on_hold" -> TrackStatus.ON_HOLD
        "dropped" -> TrackStatus.DROPPED
        "planned" -> TrackStatus.PLAN_TO_READ
        "rewatching" -> TrackStatus.RE_READING
        else -> TrackStatus.PLAN_TO_READ
    }

    private fun statusToShikimori(status: TrackStatus): String = when (status) {
        TrackStatus.READING -> "watching"
        TrackStatus.COMPLETED -> "completed"
        TrackStatus.ON_HOLD -> "on_hold"
        TrackStatus.DROPPED -> "dropped"
        TrackStatus.PLAN_TO_READ -> "planned"
        TrackStatus.RE_READING -> "rewatching"
    }
}
