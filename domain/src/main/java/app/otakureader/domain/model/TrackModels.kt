package app.otakureader.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Reading-list statuses shared by all tracker services.
 * Ordinals are used for persistence; do not reorder.
 */
@Serializable
enum class TrackStatus {
    READING,
    COMPLETED,
    ON_HOLD,
    DROPPED,
    PLAN_TO_READ,
    RE_READING;

    companion object {
        fun fromOrdinal(ordinal: Int): TrackStatus =
            entries.getOrElse(ordinal) { PLAN_TO_READ }
    }
}

/**
 * A single tracked manga entry as stored by a remote tracker service.
 */
@Immutable
@Serializable
data class TrackEntry(
    /** Unique identifier of the tracked item on the remote service. */
    val remoteId: Long,
    /** Local manga identifier this entry belongs to. */
    val mangaId: Long,
    /** ID of the tracker service (see [TrackerType]). */
    val trackerId: Int,
    val title: String = "",
    val remoteUrl: String = "",
    val status: TrackStatus = TrackStatus.PLAN_TO_READ,
    val lastChapterRead: Float = 0f,
    val totalChapters: Int = 0,
    val score: Float = 0f,
    val startDate: Long = 0L,
    val finishDate: Long = 0L
)

/** Canonical IDs for each tracker service. */
object TrackerType {
    const val MY_ANIME_LIST = 1
    const val ANILIST = 2
    const val KITSU = 3
    const val MANGA_UPDATES = 4
    const val SHIKIMORI = 5
}
