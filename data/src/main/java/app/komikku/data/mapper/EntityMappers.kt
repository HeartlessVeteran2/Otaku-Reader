package app.komikku.data.mapper

import app.komikku.core.database.entity.ChapterEntity
import app.komikku.core.database.entity.MangaEntity
import app.komikku.domain.model.Chapter
import app.komikku.domain.model.Manga
import app.komikku.domain.model.MangaStatus

/** Maps [MangaEntity] to domain [Manga]. */
fun MangaEntity.toManga(): Manga = Manga(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    author = author,
    artist = artist,
    description = description,
    genres = genres.split("|||").filter { it.isNotBlank() },
    status = MangaStatus.entries.getOrNull(status) ?: MangaStatus.UNKNOWN,
    thumbnailUrl = thumbnailUrl,
    coverLastModified = coverLastModified,
    favorite = favorite,
    dateAdded = dateAdded,
    lastUpdate = lastUpdate,
    tags = tags.split("|||").filter { it.isNotBlank() }
)

/** Maps domain [Manga] to [MangaEntity]. */
fun Manga.toEntity(): MangaEntity = MangaEntity(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    author = author,
    artist = artist,
    description = description,
    genres = genres.joinToString("|||"),
    status = status.ordinal,
    thumbnailUrl = thumbnailUrl,
    coverLastModified = coverLastModified,
    favorite = favorite,
    dateAdded = dateAdded,
    lastUpdate = lastUpdate,
    tags = tags.joinToString("|||")
)

/** Maps [ChapterEntity] to domain [Chapter]. */
fun ChapterEntity.toChapter(): Chapter = Chapter(
    id = id,
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    dateUpload = dateUpload,
    chapterNumber = chapterNumber,
    sourceOrder = sourceOrder,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    totalPageCount = totalPageCount,
    dateFetch = dateFetch
)

/** Maps domain [Chapter] to [ChapterEntity]. */
fun Chapter.toEntity(): ChapterEntity = ChapterEntity(
    id = id,
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    dateUpload = dateUpload,
    chapterNumber = chapterNumber,
    sourceOrder = sourceOrder,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    totalPageCount = totalPageCount,
    dateFetch = dateFetch
)
