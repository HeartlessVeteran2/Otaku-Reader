package app.komikku.domain.repository

import app.komikku.domain.model.Chapter
import app.komikku.domain.model.ChapterWithHistory
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for chapter data operations.
 */
interface ChapterRepository {
    /** Observe all chapters for a given manga. */
    fun observeChaptersByManga(mangaId: Long): Flow<List<Chapter>>

    /** Get a chapter by its ID. */
    suspend fun getChapter(id: Long): Chapter?

    /** Upsert chapters into the local database. */
    suspend fun upsertChapters(chapters: List<Chapter>)

    /** Mark a chapter as read or unread. */
    suspend fun setRead(id: Long, read: Boolean, lastPageRead: Int = 0)

    /** Mark a chapter as bookmarked. */
    suspend fun setBookmarked(id: Long, bookmarked: Boolean)

    /** Get reading history with chapters. */
    fun observeHistory(): Flow<List<ChapterWithHistory>>

    /** Delete history entries older than the given timestamp. */
    suspend fun deleteHistoryBefore(timestamp: Long)
}
