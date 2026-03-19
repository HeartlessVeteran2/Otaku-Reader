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
    val autoDownload: Boolean = false,
    val notes: String? = null,
    val notifyNewChapters: Boolean = true,
    /** Epoch millis when this manga was added to the library (favorited). */
    val dateAdded: Long = 0L,
    // Per-manga reader settings (#260)
    val readerDirection: Int? = null, // 0=LTR, 1=RTL
    val readerMode: Int? = null, // 0=single, 1=dual, 2=webtoon, 3=smart panels
    val readerColorFilter: Int? = null, // ColorFilterMode ordinal
    val readerCustomTintColor: Long? = null, // ARGB color
    /** Per-manga reader background color as ARGB Long, or null for default. */
    val readerBackgroundColor: Long? = null,
    // Page preloading settings (#264)
    val preloadPagesBefore: Int? = null,
    val preloadPagesAfter: Int? = null
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
