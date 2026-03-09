package app.otakureader.data.backup.mapper

import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.ReadingHistoryEntity
import app.otakureader.data.backup.model.BackupCategory
import app.otakureader.data.backup.model.BackupChapter
import app.otakureader.data.backup.model.BackupManga
import app.otakureader.data.backup.model.BackupPreferences
import app.otakureader.data.backup.model.BackupReadingHistory

/**
 * Maps [MangaEntity] to [BackupManga].
 * Chapters and category IDs should be added separately.
 */
fun MangaEntity.toBackupManga(
    chapters: List<BackupChapter> = emptyList(),
    categoryIds: List<Long> = emptyList()
): BackupManga = BackupManga(
    sourceId = sourceId,
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    author = author,
    artist = artist,
    description = description,
    genre = genre?.split("|||")?.filter { it.isNotBlank() } ?: emptyList(),
    status = status,
    favorite = favorite,
    lastUpdate = lastUpdate,
    initialized = initialized,
    viewerFlags = viewerFlags,
    chapterFlags = chapterFlags,
    coverLastModified = coverLastModified,
    dateAdded = dateAdded,
    chapters = chapters,
    categoryIds = categoryIds,
    notes = notes
)

/**
 * Maps [BackupManga] to [MangaEntity].
 * Note: This doesn't restore chapters or category associations - those must be handled separately.
 */
fun BackupManga.toMangaEntity(): MangaEntity = MangaEntity(
    id = 0, // Will be auto-generated
    sourceId = sourceId,
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    author = author,
    artist = artist,
    description = description,
    genre = genre.joinToString("|||"),
    status = status,
    favorite = favorite,
    lastUpdate = lastUpdate,
    initialized = initialized,
    viewerFlags = viewerFlags,
    chapterFlags = chapterFlags,
    coverLastModified = coverLastModified,
    dateAdded = dateAdded,
    notes = notes
)

/**
 * Maps [ChapterEntity] to [BackupChapter].
 * Reading history should be added separately if available.
 */
fun ChapterEntity.toBackupChapter(
    readingHistory: BackupReadingHistory? = null
): BackupChapter = BackupChapter(
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    chapterNumber = chapterNumber,
    sourceOrder = sourceOrder,
    dateFetch = dateFetch,
    dateUpload = dateUpload,
    lastModified = lastModified,
    readingHistory = readingHistory
)

/**
 * Maps [BackupChapter] to [ChapterEntity].
 * Requires the mangaId from the restored manga.
 */
fun BackupChapter.toChapterEntity(mangaId: Long): ChapterEntity = ChapterEntity(
    id = 0, // Will be auto-generated
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    chapterNumber = chapterNumber,
    sourceOrder = sourceOrder,
    dateFetch = dateFetch,
    dateUpload = dateUpload,
    lastModified = lastModified
)

/**
 * Maps [ReadingHistoryEntity] to [BackupReadingHistory].
 */
fun ReadingHistoryEntity.toBackupReadingHistory(): BackupReadingHistory = BackupReadingHistory(
    readAt = readAt,
    readDurationMs = readDurationMs
)

/**
 * Maps [BackupReadingHistory] to [ReadingHistoryEntity].
 * Requires the chapterId from the restored chapter.
 */
fun BackupReadingHistory.toReadingHistoryEntity(chapterId: Long): ReadingHistoryEntity = ReadingHistoryEntity(
    id = 0, // Will be auto-generated
    chapterId = chapterId,
    readAt = readAt,
    readDurationMs = readDurationMs
)

/**
 * Maps [CategoryEntity] to [BackupCategory].
 */
fun CategoryEntity.toBackupCategory(): BackupCategory = BackupCategory(
    id = id,
    name = name,
    order = order,
    flags = flags
)

/**
 * Maps [BackupCategory] to [CategoryEntity].
 * Note: ID is preserved to maintain category associations.
 */
fun BackupCategory.toCategoryEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    order = order,
    flags = flags
)

/**
 * Creates a [BackupPreferences] from individual preference values.
 */
fun createBackupPreferences(
    themeMode: Int,
    useDynamicColor: Boolean,
    locale: String,
    readerMode: Int,
    keepScreenOn: Boolean,
    volumeKeysEnabled: Boolean,
    volumeKeysInverted: Boolean,
    libraryGridSize: Int,
    showBadges: Boolean,
    updateCheckInterval: Int,
    notificationsEnabled: Boolean
): BackupPreferences = BackupPreferences(
    themeMode = themeMode,
    useDynamicColor = useDynamicColor,
    locale = locale,
    readerMode = readerMode,
    keepScreenOn = keepScreenOn,
    volumeKeysEnabled = volumeKeysEnabled,
    volumeKeysInverted = volumeKeysInverted,
    libraryGridSize = libraryGridSize,
    showBadges = showBadges,
    updateCheckInterval = updateCheckInterval,
    notificationsEnabled = notificationsEnabled
)
