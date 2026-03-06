package app.komikku.data.manga

import app.komikku.core.database.dao.ChapterDao
import app.komikku.core.database.dao.MangaDao
import app.komikku.domain.manga.model.Chapter
import app.komikku.domain.manga.model.Manga
import app.komikku.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaRepositoryImpl @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val mangaMapper: MangaMapper,
) : MangaRepository {

    override fun getLibraryManga(): Flow<List<Manga>> =
        mangaDao.getLibraryManga().map { entities -> entities.map(mangaMapper::toDomain) }

    override fun getMangaById(id: Long): Flow<Manga?> =
        mangaDao.getMangaById(id).map { entity -> entity?.let(mangaMapper::toDomain) }

    override fun getChaptersByMangaId(mangaId: Long): Flow<List<Chapter>> =
        chapterDao.getChaptersByMangaId(mangaId).map { entities -> entities.map(mangaMapper::chapterToDomain) }

    override fun getUnreadCount(mangaId: Long): Flow<Int> = chapterDao.getUnreadCount(mangaId)

    override suspend fun updateFavorite(mangaId: Long, isFavorite: Boolean) {
        mangaDao.updateFavorite(mangaId, isFavorite)
    }

    override suspend fun markChapterRead(chapterId: Long, read: Boolean) {
        chapterDao.markRead(chapterId, read)
    }

    override suspend fun markAllChaptersRead(mangaId: Long) {
        chapterDao.markAllRead(mangaId)
    }

    override suspend fun refreshManga(mangaId: Long): Manga {
        throw NotImplementedError("refreshManga not yet implemented")
    }

    override suspend fun fetchManga(sourceId: String, url: String): Manga {
        throw NotImplementedError("fetchManga not yet implemented")
    }
}
