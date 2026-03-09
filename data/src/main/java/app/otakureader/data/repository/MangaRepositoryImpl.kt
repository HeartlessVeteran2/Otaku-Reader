package app.otakureader.data.repository

import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaRepositoryImpl @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao
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
        notes = notes
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
        notes = notes
    )
}
