package app.otakureader.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Manga(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val favorite: Boolean = false,
    val initialized: Boolean = false,
    val unreadCount: Int = 0,
    val totalChapters: Int = 0,
    val lastRead: Long? = null,
    val categoryIds: List<Long> = emptyList(),
    val autoDownload: Boolean = false
)

@Serializable
enum class MangaStatus {
    UNKNOWN,
    ONGOING,
    COMPLETED,
    LICENSED,
    PUBLISHING_FINISHED,
    CANCELLED,
    ON_HIATUS;
    
    companion object {
        fun fromOrdinal(ordinal: Int): MangaStatus =
            entries.getOrElse(ordinal) { UNKNOWN }
    }
}
