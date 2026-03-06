package app.komikku.domain.repository

import app.komikku.domain.model.Manga
import app.komikku.domain.model.LibraryManga
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for manga data operations.
 * Implementations live in the `:data` module.
 */
interface MangaRepository {
    /** Observe all manga in the library. */
    fun observeLibrary(): Flow<List<LibraryManga>>

    /** Observe a single manga by ID. */
    fun observeManga(id: Long): Flow<Manga?>

    /** Get a manga by its source and URL. */
    suspend fun getMangaBySourceAndUrl(sourceId: String, url: String): Manga?

    /** Upsert a manga into the local database. */
    suspend fun upsertManga(manga: Manga): Long

    /** Toggle the favorite status of a manga. */
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** Delete a manga and its chapters from the database. */
    suspend fun deleteManga(id: Long)

    /** Search manga in the library by title. */
    fun searchLibrary(query: String): Flow<List<LibraryManga>>
}
