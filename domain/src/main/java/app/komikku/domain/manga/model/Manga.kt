package app.komikku.domain.manga.model

data class Manga(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val author: String = "",
    val artist: String = "",
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val sourceId: String = "",
    val url: String = "",
    val isFavorite: Boolean = false,
    val lastUpdate: Long = 0L,
    val dateAdded: Long = 0L,
    val unreadCount: Int = 0,
)

enum class MangaStatus {
    UNKNOWN,
    ONGOING,
    COMPLETED,
    LICENSED,
    PUBLISHING_FINISHED,
    CANCELLED,
    ON_HIATUS,
}
