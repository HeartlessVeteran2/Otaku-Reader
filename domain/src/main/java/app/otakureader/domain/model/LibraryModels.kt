package app.otakureader.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** A manga in the user's library, enriched with category and reading state. */
@Immutable
@Serializable
data class LibraryManga(
    val manga: Manga,
    val category: Long = 0L,
    val unreadCount: Int = 0,
    val readCount: Int = 0,
    val downloadedCount: Int = 0
)

/** A chapter with its reading history entry, optionally enriched with manga metadata. */
@Immutable
@Serializable
data class ChapterWithHistory(
    val chapter: Chapter,
    val readAt: Long = 0L,
    val readDurationMs: Long = 0L,
    /** The parent manga's title. Populated when loaded via a manga-joined query. */
    val mangaTitle: String? = null,
    /** The parent manga's cover URL. Populated when loaded via a manga-joined query. */
    val mangaThumbnailUrl: String? = null,
    /** Whether the parent manga is in the user's library. Populated when loaded via a manga-joined query. */
    val mangaFavorite: Boolean = false,
)

/**
 * One chapter's reading history: when it was last read and how long has been spent on it.
 *
 * Deliberately not [ChapterWithHistory], which carries the chapter and its manga alongside. This is
 * the history row on its own, for the paths that move a value from one chapter to another — a
 * migration, a restore — where loading the chapter would be work thrown away.
 */
@Immutable
@Serializable
data class ReadingHistoryEntry(
    val chapterId: Long,
    val readAt: Long,
    val readDurationMs: Long,
)

/** Manga with its latest chapter for the Updates screen. */
@Immutable
@Serializable
data class MangaUpdate(
    val manga: Manga,
    val chapter: Chapter
)

/** Reading session details for exact position resume. */
@Immutable
@Serializable
data class ReadingSession(
    val mangaId: Long,
    val chapterId: Long,
    val page: Int = 0,
    val progress: Float = 0f,
    val startedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = System.currentTimeMillis()
)
