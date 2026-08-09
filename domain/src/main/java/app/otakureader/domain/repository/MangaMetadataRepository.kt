package app.otakureader.domain.repository

import app.otakureader.domain.model.MangaMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Rich metadata for a manga, cached locally and refreshed from AniList.
 *
 * ### Offline-first, deliberately
 *
 * [observeMetadata] reads the cache and nothing else — it never triggers a fetch, and it works with
 * the radio off. [refreshMetadata] is the only thing that touches the network, and the caller
 * decides when. Splitting them this way is what keeps the details screen honest: it renders
 * whatever is cached immediately, and a failed or slow refresh changes nothing that is already on
 * screen. A combined "load" call would have to choose between blocking the first paint and
 * silently swallowing failures.
 */
interface MangaMetadataRepository {

    /** Cached metadata for [mangaId], emitting null until something has been fetched. */
    fun observeMetadata(mangaId: Long): Flow<MangaMetadata?>

    /**
     * Fetch from AniList and cache, unless a fresh copy is already held.
     *
     * @param anilistId the media to fetch. The caller resolves this — via a stored id or
     *   `MatchAniListMediaUseCase` — because matching needs search results this layer has no
     *   business fetching on its own.
     * @param force refresh even when the cached copy is still within its TTL, for an explicit
     *   user-initiated refresh.
     * @return the metadata now in the cache, or a failure carrying why the fetch did not happen.
     *   A failure never disturbs what is already cached.
     */
    suspend fun refreshMetadata(mangaId: Long, anilistId: Long, force: Boolean = false): Result<MangaMetadata>

}
