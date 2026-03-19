package app.otakureader.data.mapper

import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus

/** Maps [MangaEntity] to domain [Manga]. */
fun MangaEntity.toManga(): Manga = Manga(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    author = author,
    artist = artist,
    description = description,
    genre = genre?.split("|||")?.filter { it.isNotBlank() } ?: emptyList(),
    status = MangaStatus.entries.getOrNull(status) ?: MangaStatus.UNKNOWN,
    thumbnailUrl = thumbnailUrl,
    favorite = favorite,
    initialized = initialized,
    autoDownload = autoDownload,
    notes = notes,
    notifyNewChapters = notifyNewChapters,
    dateAdded = dateAdded,
    // Per-manga reader settings (#260)
    readerDirection = readerDirection,
    readerMode = readerMode,
    readerColorFilter = readerColorFilter,
    readerCustomTintColor = readerCustomTintColor,
    readerBackgroundColor = readerBackgroundColor,
    // Page preloading settings (#264)
    preloadPagesBefore = preloadPagesBefore,
    preloadPagesAfter = preloadPagesAfter
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
    genre = genre.joinToString("|||"),
    status = status.ordinal,
    thumbnailUrl = thumbnailUrl,
    favorite = favorite,
    initialized = initialized,
    autoDownload = autoDownload,
    notes = notes,
    notifyNewChapters = notifyNewChapters,
    dateAdded = dateAdded,
    // Per-manga reader settings (#260)
    readerDirection = readerDirection,
    readerMode = readerMode,
    readerColorFilter = readerColorFilter,
    readerCustomTintColor = readerCustomTintColor,
    readerBackgroundColor = readerBackgroundColor,
    // Page preloading settings (#264)
    preloadPagesBefore = preloadPagesBefore,
    preloadPagesAfter = preloadPagesAfter
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
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead
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
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead
)
