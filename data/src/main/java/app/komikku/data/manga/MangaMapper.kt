package app.komikku.data.manga

import app.komikku.core.database.entity.ChapterEntity
import app.komikku.core.database.entity.MangaEntity
import app.komikku.domain.manga.model.Chapter
import app.komikku.domain.manga.model.Manga
import app.komikku.domain.manga.model.MangaStatus
import javax.inject.Inject

class MangaMapper @Inject constructor() {

    fun toDomain(entity: MangaEntity): Manga = Manga(
        id = entity.id,
        title = entity.title,
        description = entity.description,
        thumbnailUrl = entity.thumbnailUrl,
        author = entity.author,
        artist = entity.artist,
        genres = entity.genres,
        status = MangaStatus.entries.getOrNull(entity.status) ?: MangaStatus.UNKNOWN,
        sourceId = entity.sourceId,
        url = entity.url,
        isFavorite = entity.isFavorite,
        lastUpdate = entity.lastUpdate,
        dateAdded = entity.dateAdded,
        unreadCount = entity.unreadCount,
    )

    fun toEntity(domain: Manga): MangaEntity = MangaEntity(
        id = domain.id,
        title = domain.title,
        description = domain.description,
        thumbnailUrl = domain.thumbnailUrl,
        author = domain.author,
        artist = domain.artist,
        genres = domain.genres,
        status = domain.status.ordinal,
        sourceId = domain.sourceId,
        url = domain.url,
        isFavorite = domain.isFavorite,
        lastUpdate = domain.lastUpdate,
        dateAdded = domain.dateAdded,
    )

    fun chapterToDomain(entity: ChapterEntity): Chapter = Chapter(
        id = entity.id,
        mangaId = entity.mangaId,
        url = entity.url,
        name = entity.name,
        dateUpload = entity.dateUpload,
        chapterNumber = entity.chapterNumber,
        scanlator = entity.scanlator,
        read = entity.read,
        bookmark = entity.bookmark,
        lastPageRead = entity.lastPageRead,
    )
}
