package app.komikku.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a manga/comic series.
 */
@Serializable
data class Manga(
    val id: Long = 0L,
    val sourceId: String,
    val url: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val thumbnailUrl: String? = null,
    val coverLastModified: Long = 0L,
    val favorite: Boolean = false,
    val dateAdded: Long = 0L,
    val lastUpdate: Long = 0L,
    val unreadCount: Int = 0,
    val downloadedCount: Int = 0,
    val tags: List<String> = emptyList()
)

/** Publication status of a manga. */
enum class MangaStatus {
    UNKNOWN,
    ONGOING,
    COMPLETED,
    LICENSED,
    PUBLISHING_FINISHED,
    CANCELLED,
    ON_HIATUS
}
