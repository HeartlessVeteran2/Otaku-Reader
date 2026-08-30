package app.otakureader.data.repository

import androidx.work.WorkManager
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.domain.repository.SyncRepository
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.ChapterWithHistoryEntity
import app.otakureader.core.database.entity.ChapterWithMangaEntity
import app.otakureader.core.database.entity.HistoryWithMangaEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.MangaStatus as DbMangaStatus
import app.otakureader.data.worker.AchievementCheckWorker
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.ChapterWithHistory
import app.otakureader.domain.model.ContinueReadingItem
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.MangaUpdate
import app.otakureader.domain.model.ReadingHistoryEntry
import app.otakureader.domain.repository.ChapterRepository
import kotlinx.coroutines.CancellationException
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
    private val readingHistoryDao: ReadingHistoryDao,
    private val workManager: WorkManager,
    private val syncRepository: SyncRepository,
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

    override suspend fun updateChapterNotes(chapterId: Long, notes: String?) {
        chapterDao.updateChapterNotes(chapterId, notes)
    }
    
    override suspend fun insertChapters(chapters: List<Chapter>) {
        // upsertAll, not a REPLACE insert: re-inserting an existing chapter must keep its id, or
        // every table storing one breaks. See ChapterDao.upsert and #1254.
        chapterDao.upsertAll(chapters.map { it.toEntity() })
    }
    
    override fun getUnreadCountByMangaId(mangaId: Long): Flow<Int> {
        return chapterDao.getUnreadCountByMangaId(mangaId)
    }

    override fun observeHistory(): Flow<List<ChapterWithHistory>> {
        return readingHistoryDao.observeHistoryWithMangaInfo().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeContinueReading(): Flow<List<ContinueReadingItem>> {
        return readingHistoryDao.observeContinueReading().map { entities ->
            entities
                .map { e ->
                    ContinueReadingItem(
                        mangaId = e.mangaId,
                        chapterId = e.chapterId,
                        mangaTitle = e.mangaTitle ?: "",
                        thumbnailUrl = e.mangaThumbnailUrl,
                        chapterName = e.name,
                        chapterNumber = e.chapterNumber,
                        lastPageRead = e.lastPageRead,
                        readAt = e.readAt
                    )
                }
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
        AchievementCheckWorker.enqueue(workManager)
        // Enqueue a sync event — best-effort, never throws so reading is never blocked.
        try {
            val chapterEntity = chapterDao.getChapterById(chapterId)
            if (chapterEntity != null) {
                syncRepository.enqueueChapterRead(
                    chapterId = chapterId,
                    mangaId = chapterEntity.mangaId,
                    chapterNumber = chapterEntity.chapterNumber,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Intentionally swallowed: sync is best-effort and must not break reading.
        }
    }

    override suspend fun removeFromHistory(chapterId: Long) {
        readingHistoryDao.deleteHistoryForChapter(chapterId)
    }

    override suspend fun clearAllHistory() {
        readingHistoryDao.deleteAll()
    }

    override suspend fun getHistoryForChapterIds(chapterIds: Collection<Long>): List<ReadingHistoryEntry> {
        // Chunked for the same bound-parameter limit as updateChapterProgress — a manga can hold
        // more than 999 chapters. The chunking also covers the empty case, which matters because
        // Room renders an empty collection as `IN ()` and SQLite rejects that outright: `chunked`
        // yields no chunks, so the query is never reached. An explicit `isEmpty()` guard here would
        // be dead code; ChapterRepositoryImplTest asserts the property instead, so removing the
        // chunking would fail rather than silently reintroduce it.
        return chapterIds.chunked(SQLITE_MAX_BIND_PARAMETERS).flatMap { chunk ->
            readingHistoryDao.getHistoryForChapters(chunk).map {
                ReadingHistoryEntry(
                    chapterId = it.chapterId,
                    readAt = it.readAt,
                    readDurationMs = it.readDurationMs,
                )
            }
        }
    }

    override suspend fun replaceHistory(entries: List<ReadingHistoryEntry>) {
        // replaceHistory, not upsert: these values are being copied from chapters that already hold
        // them, so applying the same list twice must not add the durations together. See the DAO.
        //
        // One transaction for the batch, so a migration that fails partway leaves the history as it
        // was rather than half-rewritten with nothing to say which half.
        if (entries.isEmpty()) return
        readingHistoryDao.replaceHistoryAll(
            entries.map { Triple(it.chapterId, it.readAt, it.readDurationMs) },
        )
    }

    override suspend fun getChaptersByMangaIdSync(mangaId: Long): List<Chapter> {
        return chapterDao.getChaptersByMangaId(mangaId).map { entities ->
            entities.map { it.toDomain() }
        }.first()
    }

    override suspend fun getChaptersByMangaIdsSync(mangaIds: Collection<Long>): List<Chapter> {
        // Same bound-parameter-limit chunking as updateChapterProgress(Collection<Long>, ...).
        return mangaIds.chunked(SQLITE_MAX_BIND_PARAMETERS).flatMap { chunk ->
            chapterDao.getChaptersByMangaIdsOnce(chunk).map { it.toDomain() }
        }
    }

    private fun ChapterEntity.toDomain() = Chapter(
        id = id,
        mangaId = mangaId,
        url = url,
        name = name,
        scanlator = scanlator,
        read = read,
        lastPageRead = lastPageRead,
        chapterNumber = chapterNumber,
        dateUpload = dateUpload,
        dateFetch = dateFetch,
        userNotes = userNotes
    )

    private fun ChapterWithHistoryEntity.toDomain() = ChapterWithHistory(
        chapter = chapter.toDomain(),
        readAt = history.readAt,
        readDurationMs = history.readDurationMs
    )

    private fun HistoryWithMangaEntity.toDomain() = ChapterWithHistory(
        chapter = Chapter(
            id = chapterId,
            mangaId = mangaId,
            url = url,
            name = name,
            scanlator = scanlator,
            read = read,
            lastPageRead = lastPageRead,
            chapterNumber = chapterNumber,
            dateFetch = dateFetch,
            dateUpload = dateUpload
        ),
        readAt = readAt,
        readDurationMs = readDurationMs,
        mangaTitle = mangaTitle,
        mangaThumbnailUrl = mangaThumbnailUrl,
        mangaFavorite = mangaFavorite,
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
        genre = genre?.split("|||")?.filter { it.isNotBlank() } ?: emptyList(),
        status = MangaStatus.fromOrdinal(status),
        favorite = favorite,
        initialized = initialized,
        autoDownload = autoDownload,
        dateAdded = dateAdded,
        readerBackgroundColor = readerBackgroundColor,
        chapterFlags = chapterFlags,
    )

    private fun Chapter.toEntity() = ChapterEntity(
        id = id,
        mangaId = mangaId,
        url = url,
        name = name,
        scanlator = scanlator,
        read = read,
        lastPageRead = lastPageRead,
        chapterNumber = chapterNumber,
        dateUpload = dateUpload,
        dateFetch = dateFetch,
        userNotes = userNotes
    )
}

