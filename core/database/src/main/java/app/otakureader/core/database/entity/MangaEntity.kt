package app.otakureader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "manga",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["title"]),
        Index(value = ["favorite"]),
        Index(value = ["sourceId", "url"], unique = true)
    ]
)
data class MangaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val status: Int = MangaStatus.UNKNOWN.ordinal,
    val favorite: Boolean = false,
    val lastUpdate: Long = 0,
    val initialized: Boolean = false,
    val viewerFlags: Int = 0,
    val chapterFlags: Int = 0,
    val coverLastModified: Long = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val autoDownload: Boolean = false,
    val notes: String? = null,
    val notifyNewChapters: Boolean = true,
    /** Per-manga reader background color as ARGB Long, or null for default. */
    val readerBackgroundColor: Long? = null,
    // Per-manga reader settings (#260)
    val readerDirection: Int? = null, // Reader direction enum ordinal
    val readerMode: Int? = null, // 0=single, 1=dual, 2=webtoon, 3=smart panels
    val readerColorFilter: Int? = null, // ColorFilterMode ordinal
    val readerCustomTintColor: Long? = null, // ARGB color
    // Page preloading settings (#264)
    val preloadPagesBefore: Int? = null,
    val preloadPagesAfter: Int? = null
)

enum class MangaStatus {
    UNKNOWN, ONGOING, COMPLETED, LICENSED, PUBLISHING_FINISHED, CANCELLED, ON_HIATUS
}
