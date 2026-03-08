package app.otakureader.domain.model

import kotlinx.serialization.Serializable

/**
 * A manga entry tracked on an external service.
 *
 * @param id Local database row id.
 * @param mangaId Local manga id this track is associated with.
 * @param service The tracking service.
 * @param remoteId The id of the entry on the remote service.
 * @param title Title as returned by the remote service.
 * @param lastChapterRead Last chapter number synced to the service.
 * @param totalChapters Total chapter count as reported by the service (0 = unknown).
 * @param status Current reading status on the service.
 * @param score User score on the service (0 = unset).
 * @param remoteUrl URL to the entry page on the service.
 */
@Serializable
data class TrackItem(
    val id: Long = 0,
    val mangaId: Long,
    val service: TrackService,
    val remoteId: Long,
    val title: String,
    val lastChapterRead: Float = 0f,
    val totalChapters: Int = 0,
    val status: TrackStatus = TrackStatus.READING,
    val score: Float = 0f,
    val remoteUrl: String = ""
)
