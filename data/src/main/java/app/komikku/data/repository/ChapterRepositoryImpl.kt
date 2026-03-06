package app.komikku.data.repository

import app.komikku.core.database.dao.ChapterDao
import app.komikku.core.database.dao.ReadingHistoryDao
import app.komikku.core.database.entity.ReadingHistoryEntity
import app.komikku.data.mapper.toChapter
import app.komikku.data.mapper.toEntity
import app.komikku.domain.model.Chapter
import app.komikku.domain.model.ChapterWithHistory
import app.komikku.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterRepositoryImpl @Inject constructor(
    private val chapterDao: ChapterDao,
    private val readingHistoryDao: ReadingHistoryDao
) : ChapterRepository {

    override fun observeChaptersByManga(mangaId: Long): Flow<List<Chapter>> =
        chapterDao.observeChaptersByManga(mangaId).map { list -> list.map { it.toChapter() } }

    override suspend fun getChapter(id: Long): Chapter? =
        chapterDao.getChapter(id)?.toChapter()

    override suspend fun upsertChapters(chapters: List<Chapter>) =
        chapterDao.upsertAll(chapters.map { it.toEntity() })

    override suspend fun setRead(id: Long, read: Boolean, lastPageRead: Int) =
        chapterDao.setRead(id, read, lastPageRead)

    override suspend fun setBookmarked(id: Long, bookmarked: Boolean) =
        chapterDao.setBookmarked(id, bookmarked)

    override fun observeHistory(): Flow<List<ChapterWithHistory>> =
        readingHistoryDao.observeHistory().map { list ->
            list.mapNotNull { entity ->
                val chapter = chapterDao.getChapter(entity.chapterId)?.toChapter() ?: return@mapNotNull null
                ChapterWithHistory(
                    chapter = chapter,
                    readAt = entity.readAt,
                    readDurationMs = entity.readDurationMs
                )
            }
        }

    override suspend fun deleteHistoryBefore(timestamp: Long) =
        readingHistoryDao.deleteHistoryBefore(timestamp)
}
