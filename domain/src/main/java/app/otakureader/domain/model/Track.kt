package app.otakureader.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: Long = 0,
    val mangaId: Long,
    val serviceId: Int,
    val remoteId: Long = 0,
    val title: String = "",
    val lastChapterRead: Float = 0f,
    val totalChapters: Int = 0,
    val score: Float = 0f,
    val status: TrackStatus = TrackStatus.READING,
    val startDate: Long = 0,
    val finishDate: Long = 0,
    val remoteUrl: String = ""
)

enum class TrackStatus {
    READING,
    COMPLETED,
    PLAN_TO_READ,
    DROPPED,
    ON_HOLD;

    companion object {
        fun fromOrdinal(ordinal: Int): TrackStatus =
            entries.getOrElse(ordinal) { READING }
    }
}

enum class TrackingService(val id: Int, val displayName: String) {
    MYANIMELIST(1, "MyAnimeList"),
    ANILIST(2, "AniList"),
    KITSU(3, "Kitsu");

    companion object {
        fun fromId(id: Int): TrackingService? = entries.find { it.id == id }
    }
}
