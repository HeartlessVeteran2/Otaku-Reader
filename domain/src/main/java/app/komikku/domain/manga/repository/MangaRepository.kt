package app.komikku.domain.manga.repository

import app.komikku.domain.manga.model.Chapter
import app.komikku.domain.manga.model.Manga
import kotlinx.coroutines.flow.Flow

interface MangaRepository {
    fun getLibraryManga(): Flow<List<Manga>>
    fun getMangaById(id: Long): Flow<Manga?>
    fun getChaptersByMangaId(mangaId: Long): Flow<List<Chapter>>
    fun getUnreadCount(mangaId: Long): Flow<Int>
    suspend fun updateFavorite(mangaId: Long, isFavorite: Boolean)
    suspend fun markChapterRead(chapterId: Long, read: Boolean)
    suspend fun markAllChaptersRead(mangaId: Long)
    suspend fun refreshManga(mangaId: Long): Manga
    suspend fun fetchManga(sourceId: String, url: String): Manga
}
