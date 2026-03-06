package app.komikku.domain.model

import kotlinx.serialization.Serializable

/** A manga in the user's library, enriched with category and reading state. */
@Serializable
data class LibraryManga(
    val manga: Manga,
    val category: Long = 0L,
    val unreadCount: Int = 0,
    val readCount: Int = 0,
    val downloadedCount: Int = 0
)

/** A chapter with its reading history entry. */
@Serializable
data class ChapterWithHistory(
    val chapter: Chapter,
    val readAt: Long = 0L,
    val readDurationMs: Long = 0L
)

/** Manga with its latest chapter for the Updates screen. */
@Serializable
data class MangaUpdate(
    val manga: Manga,
    val chapter: Chapter
)

/** Reading session details for exact position resume. */
@Serializable
data class ReadingSession(
    val mangaId: Long,
    val chapterId: Long,
    val page: Int = 0,
    val progress: Float = 0f,
    val startedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = System.currentTimeMillis()
)
