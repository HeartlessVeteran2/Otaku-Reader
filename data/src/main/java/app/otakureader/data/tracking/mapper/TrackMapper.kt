package app.otakureader.data.tracking.mapper

import app.otakureader.core.database.entity.TrackEntity
import app.otakureader.domain.model.Track
import app.otakureader.domain.model.TrackStatus

fun TrackEntity.toDomain(): Track {
    return Track(
        id = id,
        mangaId = mangaId,
        serviceId = serviceId,
        remoteId = remoteId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        score = score,
        status = TrackStatus.fromOrdinal(status),
        startDate = startDate,
        finishDate = finishDate,
        remoteUrl = remoteUrl
    )
}

fun Track.toEntity(): TrackEntity {
    return TrackEntity(
        id = id,
        mangaId = mangaId,
        serviceId = serviceId,
        remoteId = remoteId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        score = score,
        status = status.ordinal,
        startDate = startDate,
        finishDate = finishDate,
        remoteUrl = remoteUrl
    )
}
