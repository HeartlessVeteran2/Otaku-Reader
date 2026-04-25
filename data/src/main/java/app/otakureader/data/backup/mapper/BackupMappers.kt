package app.otakureader.data.backup.mapper

import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.FeedSavedSearchEntity
import app.otakureader.core.database.entity.FeedSourceEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.OpdsServerEntity
import app.otakureader.core.database.entity.ReadingHistoryEntity
import app.otakureader.core.database.entity.SyncConfigurationEntity
import app.otakureader.core.database.entity.TrackerSyncStateEntity
import app.otakureader.data.backup.model.BackupCategory
import app.otakureader.data.backup.model.BackupChapter
import app.otakureader.data.backup.model.BackupFeedSavedSearch
import app.otakureader.data.backup.model.BackupFeedSource
import app.otakureader.data.backup.model.BackupManga
import app.otakureader.data.backup.model.BackupOpdsServer
import app.otakureader.data.backup.model.BackupPreferences
import app.otakureader.data.backup.model.BackupReadingHistory
import app.otakureader.data.backup.model.BackupSyncConfiguration
import app.otakureader.data.backup.model.BackupTrackerSyncState
import java.time.Instant

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
    notes = notes,
    readerBackgroundColor = readerBackgroundColor,
    contentRating = contentRating,
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
    notes = notes,
    readerBackgroundColor = readerBackgroundColor,
    contentRating = contentRating,
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

fun OpdsServerEntity.toBackupOpdsServer(): BackupOpdsServer = BackupOpdsServer(
    id = id,
    name = name,
    url = url
)

fun BackupOpdsServer.toOpdsServerEntity(): OpdsServerEntity = OpdsServerEntity(
    id = 0,
    name = name,
    url = url
)

fun FeedSourceEntity.toBackupFeedSource(): BackupFeedSource = BackupFeedSource(
    id = id,
    sourceId = sourceId,
    sourceName = sourceName,
    isEnabled = isEnabled,
    itemCount = itemCount,
    order = order
)

fun BackupFeedSource.toFeedSourceEntity(): FeedSourceEntity = FeedSourceEntity(
    id = 0,
    sourceId = sourceId,
    sourceName = sourceName,
    isEnabled = isEnabled,
    itemCount = itemCount,
    order = order
)

fun FeedSavedSearchEntity.toBackupFeedSavedSearch(): BackupFeedSavedSearch = BackupFeedSavedSearch(
    id = id,
    sourceId = sourceId,
    sourceName = sourceName,
    query = query,
    filtersJson = filtersJson,
    order = order
)

fun BackupFeedSavedSearch.toFeedSavedSearchEntity(): FeedSavedSearchEntity = FeedSavedSearchEntity(
    id = id,
    sourceId = sourceId,
    sourceName = sourceName,
    query = query,
    filtersJson = filtersJson,
    order = order
)

fun TrackerSyncStateEntity.toBackupTrackerSyncState(): BackupTrackerSyncState = BackupTrackerSyncState(
    mangaId = mangaId,
    trackerId = trackerId,
    remoteId = remoteId,
    localLastChapterRead = localLastChapterRead,
    localTotalChapters = localTotalChapters,
    localStatus = localStatus,
    localLastModifiedEpochMilli = localLastModified.toEpochMilli(),
    remoteLastChapterRead = remoteLastChapterRead,
    remoteTotalChapters = remoteTotalChapters,
    remoteStatus = remoteStatus,
    remoteLastModifiedEpochMilli = remoteLastModified?.toEpochMilli(),
    syncStatus = syncStatus,
    lastSyncAttemptEpochMilli = lastSyncAttempt?.toEpochMilli(),
    lastSuccessfulSyncEpochMilli = lastSuccessfulSync?.toEpochMilli(),
    syncError = syncError
)

fun BackupTrackerSyncState.toTrackerSyncStateEntity(): TrackerSyncStateEntity = TrackerSyncStateEntity(
    id = 0,
    mangaId = mangaId,
    trackerId = trackerId,
    remoteId = remoteId,
    localLastChapterRead = localLastChapterRead,
    localTotalChapters = localTotalChapters,
    localStatus = localStatus,
    localLastModified = Instant.ofEpochMilli(localLastModifiedEpochMilli),
    remoteLastChapterRead = remoteLastChapterRead,
    remoteTotalChapters = remoteTotalChapters,
    remoteStatus = remoteStatus,
    remoteLastModified = remoteLastModifiedEpochMilli?.let { Instant.ofEpochMilli(it) },
    syncStatus = syncStatus,
    lastSyncAttempt = lastSyncAttemptEpochMilli?.let { Instant.ofEpochMilli(it) },
    lastSuccessfulSync = lastSuccessfulSyncEpochMilli?.let { Instant.ofEpochMilli(it) },
    syncError = syncError
)

fun SyncConfigurationEntity.toBackupSyncConfiguration(): BackupSyncConfiguration = BackupSyncConfiguration(
    trackerId = trackerId,
    enabled = enabled,
    syncDirection = syncDirection,
    conflictResolution = conflictResolution,
    autoSyncInterval = autoSyncInterval,
    syncOnChapterRead = syncOnChapterRead,
    syncOnMarkComplete = syncOnMarkComplete
)

fun BackupSyncConfiguration.toSyncConfigurationEntity(): SyncConfigurationEntity = SyncConfigurationEntity(
    id = 0,
    trackerId = trackerId,
    enabled = enabled,
    syncDirection = syncDirection,
    conflictResolution = conflictResolution,
    autoSyncInterval = autoSyncInterval,
    syncOnChapterRead = syncOnChapterRead,
    syncOnMarkComplete = syncOnMarkComplete
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
