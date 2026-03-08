package app.otakureader.domain.repository

import app.otakureader.domain.model.TrackItem
import app.otakureader.domain.model.TrackService
import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting track entries associated with local manga.
 */
interface TrackRepository {
    /** Returns a live stream of all track entries for the given [mangaId]. */
    fun getTracksForManga(mangaId: Long): Flow<List<TrackItem>>

    /** Returns the track entry for a specific service, or null if not tracked. */
    suspend fun getTrack(mangaId: Long, service: TrackService): TrackItem?

    /** Inserts or replaces a track entry. */
    suspend fun upsertTrack(track: TrackItem)

    /** Deletes the track entry identified by [trackId]. */
    suspend fun deleteTrack(trackId: Long)

    /** Returns all track entries for [mangaId] as a one-shot snapshot (not a Flow). */
    suspend fun getTracksSnapshot(mangaId: Long): List<TrackItem>
}
