package app.otakureader.data.repository

import app.otakureader.core.database.dao.TrackDao
import app.otakureader.data.tracking.TrackManager
import app.otakureader.data.tracking.api.AniListApi
import app.otakureader.data.tracking.api.MyAnimeListApi
import app.otakureader.data.tracking.mapper.toDomain
import app.otakureader.data.tracking.mapper.toEntity
import app.otakureader.data.tracking.model.*
import app.otakureader.domain.model.Track
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackingService
import app.otakureader.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
    private val trackManager: TrackManager,
    private val malApi: MyAnimeListApi,
    private val anilistApi: AniListApi
) : TrackRepository {

    override fun getTracksByMangaId(mangaId: Long): Flow<List<Track>> {
        return trackDao.getTracksByMangaId(mangaId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTrackByMangaAndService(mangaId: Long, serviceId: Int): Track? {
        return trackDao.getTrackByMangaAndService(mangaId, serviceId)?.toDomain()
    }

    override fun getTrackByMangaAndServiceFlow(mangaId: Long, serviceId: Int): Flow<Track?> {
        return trackDao.getTrackByMangaAndServiceFlow(mangaId, serviceId).map { it?.toDomain() }
    }

    override suspend fun insertTrack(track: Track): Long {
        return trackDao.insert(track.toEntity())
    }

    override suspend fun updateTrackProgress(mangaId: Long, serviceId: Int, lastChapterRead: Float) {
        val track = trackDao.getTrackByMangaAndService(mangaId, serviceId)
        if (track != null) {
            val updatedTrack = track.copy(lastChapterRead = lastChapterRead)
            trackDao.update(updatedTrack)
        }
    }

    override suspend fun updateTrackStatus(mangaId: Long, serviceId: Int, status: TrackStatus) {
        val track = trackDao.getTrackByMangaAndService(mangaId, serviceId)
        if (track != null) {
            val updatedTrack = track.copy(status = status.ordinal)
            trackDao.update(updatedTrack)
        }
    }

    override suspend fun updateTrackScore(mangaId: Long, serviceId: Int, score: Float) {
        val track = trackDao.getTrackByMangaAndService(mangaId, serviceId)
        if (track != null) {
            val updatedTrack = track.copy(score = score)
            trackDao.update(updatedTrack)
        }
    }

    override suspend fun deleteTrack(mangaId: Long, serviceId: Int) {
        trackDao.deleteByMangaAndService(mangaId, serviceId)
    }

    override suspend fun deleteAllTracksForManga(mangaId: Long) {
        trackDao.deleteByMangaId(mangaId)
    }

    override suspend fun syncToRemote(track: Track): Result<Track> {
        return try {
            when (track.serviceId) {
                TrackingService.MYANIMELIST.id -> syncToMyAnimeList(track)
                TrackingService.ANILIST.id -> syncToAniList(track)
                TrackingService.KITSU.id -> Result.failure(NotImplementedError("Kitsu sync not yet implemented"))
                else -> Result.failure(IllegalArgumentException("Unknown service ID: ${track.serviceId}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchManga(serviceId: Int, query: String): Result<List<Track>> {
        return try {
            when (serviceId) {
                TrackingService.MYANIMELIST.id -> searchMyAnimeList(query)
                TrackingService.ANILIST.id -> searchAniList(query)
                TrackingService.KITSU.id -> Result.failure(NotImplementedError("Kitsu search not yet implemented"))
                else -> Result.failure(IllegalArgumentException("Unknown service ID: $serviceId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun bindManga(mangaId: Long, serviceId: Int, remoteId: Long): Result<Track> {
        return try {
            val track = Track(
                mangaId = mangaId,
                serviceId = serviceId,
                remoteId = remoteId,
                lastChapterRead = 0f,
                status = TrackStatus.READING,
                title = ""
            )
            val id = insertTrack(track)
            Result.success(track.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Private helper methods ---

    private suspend fun syncToMyAnimeList(track: Track): Result<Track> {
        val accessToken = trackManager.getMalAccessToken()
            ?: return Result.failure(Exception("Not authenticated with MyAnimeList"))

        return try {
            val status = when (track.status) {
                TrackStatus.READING -> "reading"
                TrackStatus.COMPLETED -> "completed"
                TrackStatus.PLAN_TO_READ -> "plan_to_read"
                TrackStatus.DROPPED -> "dropped"
                TrackStatus.ON_HOLD -> "on_hold"
            }

            malApi.updateMangaListStatus(
                authorization = "Bearer $accessToken",
                mangaId = track.remoteId,
                status = status,
                score = track.score.toInt().takeIf { it > 0 },
                numChaptersRead = track.lastChapterRead.toInt()
            )

            Result.success(track)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncToAniList(track: Track): Result<Track> {
        val accessToken = trackManager.getAnilistAccessToken()
            ?: return Result.failure(Exception("Not authenticated with AniList"))

        return try {
            val status = when (track.status) {
                TrackStatus.READING -> "CURRENT"
                TrackStatus.COMPLETED -> "COMPLETED"
                TrackStatus.PLAN_TO_READ -> "PLANNING"
                TrackStatus.DROPPED -> "DROPPED"
                TrackStatus.ON_HOLD -> "PAUSED"
            }

            val mutation = """
                mutation(${"$"}mediaId: Int, ${"$"}status: MediaListStatus, ${"$"}score: Float, ${"$"}progress: Int) {
                    SaveMediaListEntry(mediaId: ${"$"}mediaId, status: ${"$"}status, score: ${"$"}score, progress: ${"$"}progress) {
                        id
                        status
                        score
                        progress
                    }
                }
            """.trimIndent()

            val variables = mapOf(
                "mediaId" to track.remoteId,
                "status" to status,
                "score" to track.score,
                "progress" to track.lastChapterRead.toInt()
            )

            anilistApi.graphql(
                authorization = "Bearer $accessToken",
                request = AnilistGraphQLRequest(mutation, variables)
            )

            Result.success(track)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun searchMyAnimeList(query: String): Result<List<Track>> {
        val accessToken = trackManager.getMalAccessToken()
            ?: return Result.failure(Exception("Not authenticated with MyAnimeList"))

        return try {
            val response = malApi.searchManga(
                authorization = "Bearer $accessToken",
                query = query,
                limit = 20
            )

            val tracks = response.data.map { node ->
                Track(
                    mangaId = 0, // Temp value, will be set when binding
                    serviceId = TrackingService.MYANIMELIST.id,
                    remoteId = node.node.id,
                    title = node.node.title,
                    totalChapters = node.node.numChapters ?: 0,
                    remoteUrl = "https://myanimelist.net/manga/${node.node.id}"
                )
            }

            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun searchAniList(query: String): Result<List<Track>> {
        val accessToken = trackManager.getAnilistAccessToken()
            ?: return Result.failure(Exception("Not authenticated with AniList"))

        return try {
            val searchQuery = """
                query(${"$"}search: String) {
                    Page {
                        media(search: ${"$"}search, type: MANGA) {
                            id
                            title {
                                romaji
                                english
                            }
                            chapters
                        }
                    }
                }
            """.trimIndent()

            val variables = mapOf("search" to query)

            val response = anilistApi.graphql(
                authorization = "Bearer $accessToken",
                request = AnilistGraphQLRequest(searchQuery, variables)
            )

            // Note: Simplified parsing - in production, you'd want proper JSON deserialization
            val tracks = emptyList<Track>() // Placeholder

            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
