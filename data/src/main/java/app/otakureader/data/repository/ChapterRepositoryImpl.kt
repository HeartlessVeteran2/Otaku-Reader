package app.otakureader.data.repository

import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.ChapterWithHistoryEntity
import app.otakureader.core.database.entity.ChapterWithMangaEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.MangaStatus as DbMangaStatus
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.ChapterWithHistory
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.MangaUpdate
import app.otakureader.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** SQLite's default maximum number of bound parameters per query. */
private const val SQLITE_MAX_BIND_PARAMETERS = 999

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
    
    override suspend fun updateChapterProgress(chapterIds: Collection<Long>, read: Boolean, lastPageRead: Int) {
        // SQLite's bound-parameter limit is 999. This query also binds `read` and `lastPageRead`
        // (2 parameters), so the IN (:chapterIds) list must be at most 997 to avoid
        // "too many SQL variables" at runtime.
        val chunkSize = SQLITE_MAX_BIND_PARAMETERS - 2
        chapterIds.chunked(chunkSize).forEach { chunk ->
            chapterDao.updateChapterProgress(chunk, read, lastPageRead)
        }
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

    override fun getRecentUpdates(): Flow<List<MangaUpdate>> {
        return chapterDao.getRecentUpdates().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun countNewUpdatesSince(since: Long): Flow<Int> {
        return chapterDao.countNewUpdatesSince(since)
    }

    override suspend fun recordHistory(chapterId: Long, readAt: Long, readDurationMs: Long) {
        readingHistoryDao.upsert(chapterId, readAt, readDurationMs)
    }

    override suspend fun removeFromHistory(chapterId: Long) {
        readingHistoryDao.deleteHistoryForChapter(chapterId)
    }

    override suspend fun clearAllHistory() {
        readingHistoryDao.deleteAll()
    }

    override suspend fun getChaptersByMangaIdSync(mangaId: Long): List<Chapter> {
        return chapterDao.getChaptersByMangaId(mangaId).map { entities ->
            entities.map { it.toDomain() }
        }.first()
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
        dateUpload = dateUpload,
        dateFetch = dateFetch
    )

    private fun ChapterWithHistoryEntity.toDomain() = ChapterWithHistory(
        chapter = chapter.toDomain(),
        readAt = history.readAt,
        readDurationMs = history.readDurationMs
    )

    private fun ChapterWithMangaEntity.toDomain() = MangaUpdate(
        manga = manga.toDomain(),
        chapter = chapter.toDomain()
    )

    private fun MangaEntity.toDomain() = Manga(
        id = id,
        sourceId = sourceId,
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        author = author,
        artist = artist,
        description = description,
        genre = genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
        status = MangaStatus.fromOrdinal(status),
        favorite = favorite,
        initialized = initialized,
        autoDownload = autoDownload,
        dateAdded = dateAdded,
        readerBackgroundColor = readerBackgroundColor
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
        dateUpload = dateUpload,
        dateFetch = dateFetch
    )
}

