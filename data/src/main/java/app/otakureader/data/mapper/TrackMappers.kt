package app.otakureader.data.mapper

import app.otakureader.core.database.entity.TrackEntity
import app.otakureader.domain.model.TrackItem
import app.otakureader.domain.model.TrackService
import app.otakureader.domain.model.TrackStatus

/** Maps [TrackEntity] to domain [TrackItem]. */
fun TrackEntity.toTrackItem(): TrackItem = TrackItem(
    id = id,
    mangaId = mangaId,
    service = TrackService.fromId(serviceId) ?: TrackService.MAL,
    remoteId = remoteId,
    title = title,
    lastChapterRead = lastChapterRead,
    totalChapters = totalChapters,
    status = TrackStatus.fromName(status),
    score = score,
    remoteUrl = remoteUrl
)

/** Maps domain [TrackItem] to [TrackEntity]. */
fun TrackItem.toEntity(): TrackEntity = TrackEntity(
    id = id,
    mangaId = mangaId,
    serviceId = service.id,
    remoteId = remoteId,
    title = title,
    lastChapterRead = lastChapterRead,
    totalChapters = totalChapters,
    status = status.name,
    score = score,
    remoteUrl = remoteUrl
)
