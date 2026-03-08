package app.otakureader.domain.repository

import app.otakureader.domain.model.Track
import app.otakureader.domain.model.TrackStatus
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    /**
     * Get all tracks for a manga
     */
    fun getTracksByMangaId(mangaId: Long): Flow<List<Track>>

    /**
     * Get a specific track by manga and service
     */
    suspend fun getTrackByMangaAndService(mangaId: Long, serviceId: Int): Track?

    /**
     * Observe a specific track by manga and service
     */
    fun getTrackByMangaAndServiceFlow(mangaId: Long, serviceId: Int): Flow<Track?>

    /**
     * Insert or update a track
     */
    suspend fun insertTrack(track: Track): Long

    /**
     * Update track progress (chapter read count)
     */
    suspend fun updateTrackProgress(mangaId: Long, serviceId: Int, lastChapterRead: Float)

    /**
     * Update track status (reading, completed, etc.)
     */
    suspend fun updateTrackStatus(mangaId: Long, serviceId: Int, status: TrackStatus)

    /**
     * Update track score
     */
    suspend fun updateTrackScore(mangaId: Long, serviceId: Int, score: Float)

    /**
     * Delete a track
     */
    suspend fun deleteTrack(mangaId: Long, serviceId: Int)

    /**
     * Delete all tracks for a manga
     */
    suspend fun deleteAllTracksForManga(mangaId: Long)

    /**
     * Sync track to remote service
     */
    suspend fun syncToRemote(track: Track): Result<Track>

    /**
     * Search for manga on tracking service
     */
    suspend fun searchManga(serviceId: Int, query: String): Result<List<Track>>

    /**
     * Bind a manga to a tracking service
     */
    suspend fun bindManga(mangaId: Long, serviceId: Int, remoteId: Long): Result<Track>
}
