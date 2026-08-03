package app.otakureader.data.tracking.tracker

import app.otakureader.core.preferences.TrackerTokenStore
import app.otakureader.data.tracking.api.AniListApi
import app.otakureader.data.tracking.api.AniListGraphQlQuery
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.tracking.Tracker
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
) : Tracker {

    override val id: Int = TrackerType.ANILIST
    override val name: String = "AniList"

    private var accessToken: String? = tokenStore.getTokens(TrackerType.ANILIST)?.accessToken
    private var currentUserId: Long? = tokenStore.getTokens(TrackerType.ANILIST)?.userId

    override val isLoggedIn: Boolean
        get() = accessToken != null

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
