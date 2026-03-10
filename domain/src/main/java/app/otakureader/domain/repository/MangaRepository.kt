package app.otakureader.domain.repository

import app.otakureader.domain.model.Manga
import kotlinx.coroutines.flow.Flow

interface MangaRepository {
    fun getLibraryManga(): Flow<List<Manga>>
    fun searchLibraryManga(query: String): Flow<List<Manga>>
    suspend fun getMangaById(id: Long): Manga?
    fun getMangaByIdFlow(id: Long): Flow<Manga?>
    suspend fun getMangaBySourceAndUrl(sourceId: Long, url: String): Manga?
    suspend fun getMangaByIds(ids: List<Long>): List<Manga>
    suspend fun insertManga(manga: Manga): Long
    suspend fun updateManga(manga: Manga)
    suspend fun deleteManga(id: Long)
    suspend fun toggleFavorite(id: Long)
    suspend fun updateAutoDownload(id: Long, autoDownload: Boolean)
    fun isFavorite(id: Long): Flow<Boolean>
    suspend fun updateMangaNote(id: Long, notes: String?)
    suspend fun updateNotifyNewChapters(id: Long, notify: Boolean)

    // Per-manga reader settings (#260)
    suspend fun updateReaderDirection(id: Long, direction: Int?)
    suspend fun updateReaderMode(id: Long, mode: Int?)
    suspend fun updateReaderColorFilter(id: Long, filter: Int?)
    suspend fun updateReaderCustomTintColor(id: Long, color: Long?)

    // Page preloading settings (#264)
    suspend fun updatePreloadPagesBefore(id: Long, count: Int?)
    suspend fun updatePreloadPagesAfter(id: Long, count: Int?)
}
