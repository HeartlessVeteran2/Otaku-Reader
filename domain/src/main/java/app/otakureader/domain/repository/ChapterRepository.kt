package app.otakureader.domain.repository

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.ChapterWithHistory
import app.otakureader.domain.model.ContinueReadingItem
import app.otakureader.domain.model.MangaUpdate
import app.otakureader.domain.model.ReadingHistoryEntry
import kotlinx.coroutines.flow.Flow

interface ChapterRepository {
    fun getChaptersByMangaId(mangaId: Long): Flow<List<Chapter>>
    suspend fun getChapterById(id: Long): Chapter?
    fun getChapterByIdFlow(id: Long): Flow<Chapter?>
    suspend fun getNextUnreadChapter(mangaId: Long): Chapter?
    suspend fun updateChapterProgress(chapterId: Long, read: Boolean, lastPageRead: Int)
    suspend fun updateChapterProgress(chapterIds: Collection<Long>, read: Boolean, lastPageRead: Int)
    suspend fun updateChapterNotes(chapterId: Long, notes: String?)
    suspend fun insertChapters(chapters: List<Chapter>)
    fun getUnreadCountByMangaId(mangaId: Long): Flow<Int>
    fun observeHistory(): Flow<List<ChapterWithHistory>>
    /** Returns the most-recently read chapter per favorited manga for the "Continue Reading" carousel. */
    fun observeContinueReading(): Flow<List<ContinueReadingItem>>
    fun getRecentUpdates(): Flow<List<MangaUpdate>>
    /** Count library chapters fetched after [since] (epoch millis) for the Updates badge. */
    fun countNewUpdatesSince(since: Long): Flow<Int>
    suspend fun recordHistory(chapterId: Long, readAt: Long, readDurationMs: Long)
    suspend fun removeFromHistory(chapterId: Long)
    suspend fun clearAllHistory()

    /**
     * The history rows belonging to [chapterIds], and nothing else.
     *
     * [observeHistory] is the whole app's history; anything wanting one manga's would have had to
     * load that and filter. Chapters with no history simply do not appear, so the result can be
     * shorter than the input — it is a lookup, not a per-id mapping.
     */
    suspend fun getHistoryForChapterIds(chapterIds: Collection<Long>): List<ReadingHistoryEntry>

    /**
     * Writes [entries] verbatim, replacing whatever those chapters' history rows hold.
     *
     * This is the copy path, distinct from [recordHistory], which *accumulates* duration because it
     * is recording a reading session. Here the values already exist elsewhere and are being moved,
     * so applying the same list twice must leave the same result — a migration re-run must not
     * double the reading time.
     */
    suspend fun replaceHistory(entries: List<ReadingHistoryEntry>)

    /** Migration-specific methods */
    suspend fun getChaptersByMangaIdSync(mangaId: Long): List<Chapter>

    /** Batched variant of [getChaptersByMangaIdSync] for bulk actions across multiple manga. */
    suspend fun getChaptersByMangaIdsSync(mangaIds: Collection<Long>): List<Chapter>
}
