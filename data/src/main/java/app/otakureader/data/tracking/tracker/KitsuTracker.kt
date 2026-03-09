package app.otakureader.data.tracking.tracker

import app.otakureader.data.tracking.api.KitsuApi
import app.otakureader.data.tracking.api.KitsuLibraryEntryAttributes
import app.otakureader.data.tracking.api.KitsuLibraryEntryData
import app.otakureader.data.tracking.api.KitsuLibraryEntryRelationships
import app.otakureader.data.tracking.api.KitsuLibraryEntryRequest
import app.otakureader.data.tracking.api.KitsuOAuthApi
import app.otakureader.data.tracking.api.KitsuRelationshipData
import app.otakureader.data.tracking.api.KitsuResourceIdentifier
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.tracking.Tracker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracker implementation for [Kitsu](https://kitsu.app/).
 *
 * Authentication uses the Kitsu OAuth 2.0 password-grant flow.
 * Kitsu status strings map as follows:
 *  - "current"   → READING
 *  - "completed" → COMPLETED
 *  - "on_hold"   → ON_HOLD
 *  - "dropped"   → DROPPED
 *  - "planned"   → PLAN_TO_READ
 */
class KitsuTracker(
    private val oauthApi: KitsuOAuthApi,
    private val api: KitsuApi,
    private val clientId: String,
    private val clientSecret: String
) : Tracker {

    override val id: Int = TrackerType.KITSU
    override val name: String = "Kitsu"

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var userId: Long? = null
    private val tokenMutex = Mutex()

    override val isLoggedIn: Boolean
        get() = accessToken != null

    override suspend fun login(username: String, password: String): Boolean {
        return try {
            val response = oauthApi.getAccessToken(
                username = username,
                password = password,
                clientId = clientId,
                clientSecret = clientSecret
            )
            tokenMutex.withLock {
                accessToken = response.accessToken
                refreshToken = response.refreshToken
            }
            val uid = fetchCurrentUserId()
            if (uid != null) {
                userId = uid
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
        userId = null
    }

    override suspend fun search(query: String): List<TrackEntry> {
        return api.searchManga(query = query).data.map { resource ->
            TrackEntry(
                remoteId = resource.id.toLongOrNull() ?: 0L,
                mangaId = 0L,
                trackerId = id,
                title = resource.attributes.canonicalTitle,
                totalChapters = resource.attributes.chapterCount ?: 0
            )
        }
    }

    override suspend fun find(remoteId: Long): TrackEntry? {
        val uid = userId ?: return null
        val entries = api.findLibraryEntry(mangaId = remoteId, userId = uid).data
        val entry = entries.firstOrNull() ?: return null
        val attrs = entry.attributes
        return TrackEntry(
            remoteId = remoteId,
            mangaId = 0L,
            trackerId = id,
            status = statusFromKitsu(attrs.status ?: "planned"),
            lastChapterRead = attrs.progressedChapters.toFloat(),
            score = attrs.ratingTwenty?.div(2f) ?: 0f
        )
    }

    override suspend fun update(entry: TrackEntry): TrackEntry {
        val uid = userId ?: return entry
        val existing = api.findLibraryEntry(mangaId = entry.remoteId, userId = uid).data.firstOrNull()
        val attrs = KitsuLibraryEntryAttributes(
            status = statusToKitsu(entry.status),
            progressedChapters = entry.lastChapterRead.toInt(),
            ratingTwenty = if (entry.score > 0f) (entry.score * 2).toInt() else null
        )
        if (existing == null) {
            val request = KitsuLibraryEntryRequest(
                data = KitsuLibraryEntryData(
                    attributes = attrs,
                    relationships = KitsuLibraryEntryRelationships(
                        manga = KitsuRelationshipData(
                            KitsuResourceIdentifier("manga", entry.remoteId.toString())
                        ),
                        user = KitsuRelationshipData(
                            KitsuResourceIdentifier("users", uid.toString())
                        )
                    )
                )
            )
            api.createLibraryEntry(request)
        } else {
            val entryId = existing.id.toLongOrNull() ?: return entry
            val request = KitsuLibraryEntryRequest(
                data = KitsuLibraryEntryData(attributes = attrs)
            )
            api.updateLibraryEntry(entryId, request)
        }
        return entry
    }

    override fun toTrackStatus(remoteStatus: Int): TrackStatus = TrackStatus.fromOrdinal(remoteStatus)

    override fun toRemoteStatus(status: TrackStatus): Int = status.ordinal

    private fun statusFromKitsu(kitsuStatus: String): TrackStatus = when (kitsuStatus) {
        "current" -> TrackStatus.READING
        "completed" -> TrackStatus.COMPLETED
        "on_hold" -> TrackStatus.ON_HOLD
        "dropped" -> TrackStatus.DROPPED
        "planned" -> TrackStatus.PLAN_TO_READ
        else -> TrackStatus.PLAN_TO_READ
    }

    private fun statusToKitsu(status: TrackStatus): String = when (status) {
        TrackStatus.READING -> "current"
        TrackStatus.COMPLETED -> "completed"
        TrackStatus.ON_HOLD -> "on_hold"
        TrackStatus.DROPPED -> "dropped"
        TrackStatus.PLAN_TO_READ -> "planned"
        TrackStatus.RE_READING -> "current"
    }

    private suspend fun fetchCurrentUserId(): Long? {
        return try {
            api.getCurrentUser().data.firstOrNull()?.id?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
