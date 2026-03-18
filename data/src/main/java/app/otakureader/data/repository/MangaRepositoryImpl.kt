package app.otakureader.data.repository

import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaRepositoryImpl @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val downloadRepository: dagger.Lazy<app.otakureader.domain.repository.DownloadRepository>
) : MangaRepository {

    override fun getLibraryManga(): Flow<List<Manga>> {
        return mangaDao.getFavoriteMangaWithUnreadCount().map { mangaWithUnreadList ->
            mangaWithUnreadList.map { it.manga.toDomain(it.unreadCount) }
        }
    }

    override fun searchLibraryManga(query: String): Flow<List<Manga>> {
        return mangaDao.searchFavoriteManga(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMangaById(id: Long): Manga? {
        return mangaDao.getMangaById(id)?.toDomain()
    }

    override suspend fun getMangaBySourceAndUrl(sourceId: Long, url: String): Manga? {
        return mangaDao.getMangaBySourceAndUrl(sourceId, url)?.toDomain()
    }

    override suspend fun getMangaByIds(ids: List<Long>): List<Manga> {
        if (ids.isEmpty()) return emptyList()
        // Chunk to stay within SQLite's 999 bind-parameter limit, then re-order to match `ids`
        val resultMap = ids.chunked(997).flatMap { chunk ->
            mangaDao.getMangaByIds(chunk).map { it.toDomain() }
        }.associateBy { it.id }
        return ids.mapNotNull { resultMap[it] }
    }

    override fun getMangaByIdFlow(id: Long): Flow<Manga?> {
        return combine(
            mangaDao.getMangaByIdFlow(id),
            chapterDao.getUnreadCountByMangaId(id)
        ) { mangaEntity, unreadCount ->
            mangaEntity?.toDomain(unreadCount)
        }
    }

    override suspend fun insertManga(manga: Manga): Long {
        return mangaDao.insert(manga.toEntity())
    }

    override suspend fun updateManga(manga: Manga) {
        mangaDao.update(manga.toEntity())
    }

    override suspend fun deleteManga(id: Long) {
        mangaDao.deleteById(id)
    }

    override suspend fun toggleFavorite(id: Long) {
        val manga = mangaDao.getMangaById(id) ?: return
        mangaDao.updateFavorite(id, !manga.favorite)
    }

    override suspend fun updateAutoDownload(id: Long, autoDownload: Boolean) {
        mangaDao.updateAutoDownload(id, autoDownload)
    }

    override fun isFavorite(id: Long): Flow<Boolean> {
        return mangaDao.isFavorite(id)
    }

    override suspend fun updateMangaNote(id: Long, notes: String?) {
        mangaDao.updateNote(id, notes)
    }

    override suspend fun updateNotifyNewChapters(id: Long, notify: Boolean) {
        mangaDao.updateNotifyNewChapters(id, notify)
    }

    // Per-manga reader settings (#260)
    override suspend fun updateReaderDirection(id: Long, direction: Int?) {
        mangaDao.updateReaderDirection(id, direction)
    }

    override suspend fun updateReaderMode(id: Long, mode: Int?) {
        mangaDao.updateReaderMode(id, mode)
    }

    override suspend fun updateReaderColorFilter(id: Long, filter: Int?) {
        mangaDao.updateReaderColorFilter(id, filter)
    }

    override suspend fun updateReaderCustomTintColor(id: Long, color: Long?) {
        mangaDao.updateReaderCustomTintColor(id, color)
    }

    override suspend fun updateReaderBackgroundColor(id: Long, color: Long?) {
        mangaDao.updateReaderBackgroundColor(id, color)
    }

    // Page preloading settings (#264)
    override suspend fun updatePreloadPagesBefore(id: Long, count: Int?) {
        mangaDao.updatePreloadPagesBefore(id, count)
    }

    override suspend fun updatePreloadPagesAfter(id: Long, count: Int?) {
        mangaDao.updatePreloadPagesAfter(id, count)
    }

    // Bulk operations
    override suspend fun addToFavorites(id: Long) {
        mangaDao.updateFavorite(id, true)
    }

    override suspend fun removeFromFavorites(id: Long) {
        mangaDao.updateFavorite(id, false)
    }

    override suspend fun addMangaToCategory(mangaId: Long, categoryId: Long) {
        // Implemented via CategoryRepository to avoid circular dependency
        // This is a placeholder - actual implementation uses CategoryDao
    }

    override suspend fun deleteDownloadsForManga(mangaId: Long) {
        // Get manga details to obtain source ID and title
        val manga = mangaDao.getMangaById(mangaId) ?: return

        // Get all chapters for this manga
        val chapters = chapterDao.getChaptersByMangaId(mangaId).first()

        // Delete downloads for each chapter
        val sourceName = manga.sourceId.toString()
        val mangaTitle = manga.title

        chapters.forEach { chapterEntity ->
            downloadRepository.get().deleteChapterDownload(
                chapterId = chapterEntity.id,
                sourceName = sourceName,
                mangaTitle = mangaTitle,
                chapterTitle = chapterEntity.name
            )
        }
    }

    private fun MangaEntity.toDomain(unreadCount: Int = 0) = Manga(
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
        unreadCount = unreadCount,
        autoDownload = autoDownload,
        notes = notes,
        notifyNewChapters = notifyNewChapters,
        // Per-manga reader settings (#260)
        readerDirection = readerDirection,
        readerMode = readerMode,
        readerColorFilter = readerColorFilter,
        readerCustomTintColor = readerCustomTintColor,
        readerBackgroundColor = readerBackgroundColor,
        // Page preloading settings (#264)
        preloadPagesBefore = preloadPagesBefore,
        preloadPagesAfter = preloadPagesAfter
    )

    private fun Manga.toEntity() = MangaEntity(
        id = id,
        sourceId = sourceId,
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        author = author,
        artist = artist,
        description = description,
        genre = genre.joinToString(","),
        status = status.ordinal,
        favorite = favorite,
        initialized = initialized,
        autoDownload = autoDownload,
        notes = notes,
        notifyNewChapters = notifyNewChapters,
        // Per-manga reader settings (#260)
        readerDirection = readerDirection,
        readerMode = readerMode,
        readerColorFilter = readerColorFilter,
        readerCustomTintColor = readerCustomTintColor,
        readerBackgroundColor = readerBackgroundColor,
        // Page preloading settings (#264)
        preloadPagesBefore = preloadPagesBefore,
        preloadPagesAfter = preloadPagesAfter
    )
}
