package app.otakureader.data.repository

import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.ChapterWithHistoryEntity
import app.otakureader.core.database.entity.ReadingHistoryEntity
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.ChapterWithHistory
import app.otakureader.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterRepositoryImpl @Inject constructor(
    private val chapterDao: ChapterDao,
    private val readingHistoryDao: ReadingHistoryDao
) : ChapterRepository {
    
    override fun getChaptersByMangaId(mangaId: Long): Flow<List<Chapter>> {
        return chapterDao.getChaptersByMangaId(mangaId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getChapterById(id: Long): Chapter? {
        return chapterDao.getChapterById(id)?.toDomain()
    }
    
    override fun getChapterByIdFlow(id: Long): Flow<Chapter?> {
        return chapterDao.getChapterByIdFlow(id).map { it?.toDomain() }
    }
    
    override suspend fun getNextUnreadChapter(mangaId: Long): Chapter? {
        return chapterDao.getNextUnreadChapter(mangaId)?.toDomain()
    }
    
    override suspend fun updateChapterProgress(chapterId: Long, read: Boolean, lastPageRead: Int) {
        chapterDao.updateChapterProgress(chapterId, read, lastPageRead)
    }
    
    override suspend fun updateBookmark(chapterId: Long, bookmark: Boolean) {
        chapterDao.updateBookmark(chapterId, bookmark)
    }
    
    override suspend fun insertChapters(chapters: List<Chapter>) {
        chapterDao.insertAll(chapters.map { it.toEntity() })
    }
    
    override fun getUnreadCountByMangaId(mangaId: Long): Flow<Int> {
        return chapterDao.getUnreadCountByMangaId(mangaId)
    }

    override fun observeHistory(): Flow<List<ChapterWithHistory>> {
        return readingHistoryDao.observeHistoryWithChapters().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun recordHistory(chapterId: Long, readAt: Long, readDurationMs: Long) {
        readingHistoryDao.upsert(
            ReadingHistoryEntity(
                chapterId = chapterId,
                readAt = readAt,
                readDurationMs = readDurationMs
            )
        )
    }

    override suspend fun removeFromHistory(chapterId: Long) {
        readingHistoryDao.deleteHistoryForChapter(chapterId)
    }

    override suspend fun clearAllHistory() {
        readingHistoryDao.deleteAll()
    }

    private fun ChapterEntity.toDomain() = Chapter(
        id = id,
        mangaId = mangaId,
        url = url,
        name = name,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        chapterNumber = chapterNumber,
        dateUpload = dateUpload
    )
    
    private fun Chapter.toEntity() = ChapterEntity(
        id = id,
        mangaId = mangaId,
        url = url,
        name = name,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        chapterNumber = chapterNumber,
        dateUpload = dateUpload
    )
}

