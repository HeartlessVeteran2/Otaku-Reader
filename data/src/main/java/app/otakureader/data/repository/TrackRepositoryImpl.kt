package app.otakureader.data.repository

import app.otakureader.core.database.dao.TrackDao
import app.otakureader.data.mapper.toEntity
import app.otakureader.data.mapper.toTrackItem
import app.otakureader.domain.model.TrackItem
import app.otakureader.domain.model.TrackService
import app.otakureader.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao
) : TrackRepository {

    override fun getTracksForManga(mangaId: Long): Flow<List<TrackItem>> =
        trackDao.getTracksForManga(mangaId).map { entities -> entities.map { it.toTrackItem() } }

    override suspend fun getTrack(mangaId: Long, service: TrackService): TrackItem? =
        trackDao.getTrack(mangaId, service.id)?.toTrackItem()

    override suspend fun upsertTrack(track: TrackItem) =
        trackDao.upsert(track.toEntity())

    override suspend fun deleteTrack(trackId: Long) =
        trackDao.deleteById(trackId)

    override suspend fun getTracksSnapshot(mangaId: Long): List<TrackItem> =
        trackDao.getTracksForManga(mangaId).first().map { it.toTrackItem() }
}
