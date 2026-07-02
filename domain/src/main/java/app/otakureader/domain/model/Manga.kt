package app.otakureader.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Domain model representing a manga series.
 *
 * **UI Performance Note:** Marked with [@Immutable] to prevent unnecessary
 * recompositions in Jetpack Compose. All properties are immutable (val).
 */
@Immutable
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
    /** Epoch millis when a new chapter was last published for this manga. */
    val lastUpdate: Long = 0L,
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
    val preloadPagesAfter: Int? = null,
    val contentRating: ContentRating = ContentRating.SAFE,
    /** User has marked this manga as fully read. */
    val userCompleted: Boolean = false,
    /** User has dropped / abandoned this manga. */
    val userDropped: Boolean = false,
    /**
     * Per-manga override for the cover-derived dynamic theme (#947).
     * null = inherit the global `autoThemeColor` setting; true/false = explicit override.
     */
    val mangaThemeOverride: Boolean? = null,

    /** True when the user has manually overridden at least one metadata field (#998). */
    val isUserEdited: Boolean = false,

    /** True when [thumbnailUrl] is a user-chosen custom cover rather than the source cover. */
    val hasCustomCover: Boolean = false,

    /**
     * Chapter list sort direction + read/downloaded filter state, packed using the same bit
     * layout as Tachiyomi/Mihon's `Manga.chapterFlags` so it round-trips meaningfully through
     * native backup export/import. See `chapterFlagsOf`/`chapterSortOrderFromFlags`/
     * `chapterFilterFromFlags` in `feature/details` for the encode/decode.
     */
    val chapterFlags: Int = 0,
)

@Serializable
enum class ContentRating {
    SAFE,
    SUGGESTIVE,
    EROTICA,
    PORNOGRAPHIC;

    companion object {
        fun fromOrdinal(ordinal: Int): ContentRating =
            entries.getOrElse(ordinal) { SAFE }
    }
}

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
