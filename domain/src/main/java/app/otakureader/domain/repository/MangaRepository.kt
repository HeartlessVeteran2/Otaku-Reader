package app.otakureader.domain.repository

import app.otakureader.domain.model.Manga
import kotlinx.coroutines.flow.Flow

interface MangaRepository {
    fun getLibraryManga(): Flow<List<Manga>>
    fun searchLibraryManga(query: String): Flow<List<Manga>>
    suspend fun getMangaById(id: Long): Manga?
    fun getMangaByIdFlow(id: Long): Flow<Manga?>
    suspend fun insertManga(manga: Manga): Long
    suspend fun updateManga(manga: Manga)
    suspend fun deleteManga(id: Long)
    suspend fun toggleFavorite(id: Long)
    suspend fun updateAutoDownload(id: Long, autoDownload: Boolean)
    fun isFavorite(id: Long): Flow<Boolean>
}
