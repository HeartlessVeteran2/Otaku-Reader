package app.otakureader.domain.repository

import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
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
    suspend fun updateReaderBackgroundColor(id: Long, color: Long?)

    // Page preloading settings (#264)
    suspend fun updatePreloadPagesBefore(id: Long, count: Int?)
    suspend fun updatePreloadPagesAfter(id: Long, count: Int?)

    // Bulk operations
    suspend fun addToFavorites(id: Long)
    suspend fun removeFromFavorites(id: Long)
    suspend fun addMangaToCategory(mangaId: Long, categoryId: Long)
    suspend fun deleteDownloadsForManga(mangaId: Long)

    // Completed series tracking
    suspend fun markUserCompleted(id: Long, completed: Boolean)
    fun getCompletedManga(): Flow<List<Manga>>
    fun getActiveManga(): Flow<List<Manga>>

    // Dropped series tracking
    suspend fun markUserDropped(id: Long, dropped: Boolean)
    fun getDroppedManga(): Flow<List<Manga>>

    /** Per-manga cover theme override (#947). Pass null to inherit global pref. */
    suspend fun updateMangaThemeOverride(id: Long, override: Boolean?)

    /** Persists chapter list sort direction + read/downloaded filter state. */
    suspend fun updateChapterFlags(id: Long, flags: Int)

    // User-info overrides (#998)
    /**
     * Persist user-edited metadata overrides. A null argument clears that field's override
     * (the source value is then shown). Pass all nulls to fully reset.
     */
    suspend fun updateLocalOverrides(
        id: Long,
        title: String?,
        description: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        genres: List<String>?,
        status: MangaStatus?,
    )

    /** Remove all user-info overrides for this manga, reverting to source metadata. */
    suspend fun clearLocalOverrides(id: Long)

    // Custom cover art
    /**
     * Copies the image at [contentUri] (a `content://` URI from the system image picker)
     * into app-private storage and sets it as this manga's cover. Other user-info
     * overrides are left untouched.
     */
    suspend fun setCustomCover(id: Long, contentUri: String)

    /** Deletes the stored custom cover file (if any) and reverts to the source cover. */
    suspend fun removeCustomCover(id: Long)

    // Duplicate detection (#997)
    /** Groups of library manga sharing the same normalised title (≥2 entries per group). */
    fun findDuplicates(): Flow<List<List<Manga>>>

    /** Returns the category IDs that a manga belongs to. */
    suspend fun getCategoryIdsForManga(mangaId: Long): List<Long>

    // Alternative source linking (#1053)
    /** Links two library manga as alternative versions of the same series. */
    suspend fun linkAlternativeSource(mangaId: Long, altMangaId: Long)

    /** Removes the alternative-source link between two manga. */
    suspend fun unlinkAlternativeSource(mangaId: Long, altMangaId: Long)

    /** Returns IDs of all manga linked as alternatives to the given manga. */
    suspend fun getAlternativeSourceIds(mangaId: Long): List<Long>
}
