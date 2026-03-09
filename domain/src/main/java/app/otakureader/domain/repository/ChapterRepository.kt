package app.otakureader.domain.repository

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.ChapterWithHistory
import kotlinx.coroutines.flow.Flow

interface ChapterRepository {
    fun getChaptersByMangaId(mangaId: Long): Flow<List<Chapter>>
    suspend fun getChapterById(id: Long): Chapter?
    fun getChapterByIdFlow(id: Long): Flow<Chapter?>
    suspend fun getNextUnreadChapter(mangaId: Long): Chapter?
    suspend fun updateChapterProgress(chapterId: Long, read: Boolean, lastPageRead: Int)
    suspend fun updateChapterProgress(chapterIds: List<Long>, read: Boolean, lastPageRead: Int)
    suspend fun updateBookmark(chapterId: Long, bookmark: Boolean)
    suspend fun insertChapters(chapters: List<Chapter>)
    fun getUnreadCountByMangaId(mangaId: Long): Flow<Int>
    fun observeHistory(): Flow<List<ChapterWithHistory>>
    suspend fun recordHistory(chapterId: Long, readAt: Long, readDurationMs: Long)
    suspend fun removeFromHistory(chapterId: Long)
    suspend fun clearAllHistory()
}
