package app.otakureader.data.mapper

import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.ContentRating
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
    lastUpdate = lastUpdate,
    // Per-manga reader settings (#260)
    readerDirection = readerDirection,
    readerMode = readerMode,
    readerColorFilter = readerColorFilter,
    readerCustomTintColor = readerCustomTintColor,
    readerBackgroundColor = readerBackgroundColor,
    // Page preloading settings (#264)
    preloadPagesBefore = preloadPagesBefore,
    preloadPagesAfter = preloadPagesAfter,
    contentRating = ContentRating.fromOrdinal(contentRating),
    chapterFlags = chapterFlags,
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
    // Was omitted, so every insert/update (e.g. a library refresh, which does a full-row REPLACE)
    // silently reset lastUpdate to 0, corrupting "recently updated" sorting and update tracking.
    lastUpdate = lastUpdate,
    // Per-manga reader settings (#260)
    readerDirection = readerDirection,
    readerMode = readerMode,
    readerColorFilter = readerColorFilter,
    readerCustomTintColor = readerCustomTintColor,
    readerBackgroundColor = readerBackgroundColor,
    // Page preloading settings (#264)
    preloadPagesBefore = preloadPagesBefore,
    preloadPagesAfter = preloadPagesAfter,
    contentRating = contentRating.ordinal,
    chapterFlags = chapterFlags,
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
    lastPageRead = lastPageRead,
    dateFetch = dateFetch,
    userNotes = userNotes,
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
    lastPageRead = lastPageRead,
    // Were omitted, so a full-row REPLACE during chapter refresh zeroed dateFetch (breaking
    // "recent updates" sorting) and erased userNotes. Match the complete mapper in
    // ChapterRepositoryImpl.
    dateFetch = dateFetch,
    userNotes = userNotes,
)
