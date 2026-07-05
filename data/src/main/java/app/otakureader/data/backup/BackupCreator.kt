package app.otakureader.data.backup

import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.FeedDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.OpdsServerDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.dao.TrackerSyncDao
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.data.backup.mapper.createBackupPreferences
import app.otakureader.data.backup.mapper.toBackupCategory
import app.otakureader.data.backup.mapper.toBackupChapter
import app.otakureader.data.backup.mapper.toBackupFeedSavedSearch
import app.otakureader.data.backup.mapper.toBackupFeedSource
import app.otakureader.data.backup.mapper.toBackupManga
import app.otakureader.data.backup.mapper.toBackupOpdsServer
import app.otakureader.data.backup.mapper.toBackupReadingHistory
import app.otakureader.data.backup.mapper.toBackupSyncConfiguration
import app.otakureader.data.backup.mapper.toBackupTrackerSyncState
import app.otakureader.data.backup.model.BackupData
import app.otakureader.data.backup.model.BackupManga
import app.otakureader.data.backup.model.BackupPreferences
import app.otakureader.domain.model.BackupOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import javax.inject.Inject

/**
 * Creates backup data by serializing library, categories, history, and preferences to JSON.
 */
class BackupCreator @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val categoryDao: CategoryDao,
    private val readingHistoryDao: ReadingHistoryDao,
    private val trackerSyncDao: TrackerSyncDao,
    private val opdsServerDao: OpdsServerDao,
    private val feedDao: FeedDao,
    private val generalPreferences: GeneralPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Streams a full backup directly to [output], serialising one manga at a time to avoid
     * holding the entire library + chapters + JSON string in RAM simultaneously.
     * Callers should wrap [output] in a [java.io.BufferedOutputStream] if the underlying
     * stream is not already buffered.
     *
     * Sections deselected via [options] are written as empty arrays (or `null` for
     * preferences) rather than omitted — the JSON envelope/decoder is unchanged, no version
     * bump needed. See [BackupOptions] for the dependency gating rules (chapters/tracking
     * require libraryEntries).
     */
    @Suppress("LongMethod", "CognitiveComplexMethod")
    suspend fun createBackupToStream(output: OutputStream, options: BackupOptions = BackupOptions.ALL) =
        withContext(Dispatchers.IO) {
            val writer = output.bufferedWriter(Charsets.UTF_8)
            writer.write("""{"version":${BackupData.CURRENT_VERSION},"createdAt":${System.currentTimeMillis()},"manga":[""")

            if (options.libraryEntries) {
                // Load history map once — it's ID+timestamps, compact even for large libraries.
                val historyByChapterId = readingHistoryDao.observeHistory().first().associateBy { it.chapterId }
                val favoriteManga = mangaDao.getFavoriteManga().first()

                favoriteManga.forEachIndexed { index, mangaEntity ->
                    if (index > 0) writer.write(",")
                    val backupChapters = if (options.effectiveChapters) {
                        chapterDao.getChaptersByMangaId(mangaEntity.id).first().map { chapterEntity ->
                            chapterEntity.toBackupChapter(
                                readingHistory = historyByChapterId[chapterEntity.id]?.toBackupReadingHistory()
                            )
                        }
                    } else {
                        emptyList()
                    }
                    val categoryIds = if (options.categories) {
                        categoryDao.getCategoryIdsForManga(mangaEntity.id).first()
                    } else {
                        emptyList()
                    }
                    writer.write(json.encodeToString(mangaEntity.toBackupManga(chapters = backupChapters, categoryIds = categoryIds)))
                    // Flush every 50 manga so buffered bytes don't accumulate unbounded.
                    if (index % 50 == 49) writer.flush()
                }
            }

            val preferencesBackup: BackupPreferences? =
                if (options.preferences) createPreferencesBackup() else null

            writer.write("""],"categories":""")
            writer.write(json.encodeToString(if (options.categories) createCategoryBackup() else emptyList()))
            writer.write(""","preferences":""")
            writer.write(json.encodeToString(preferencesBackup))
            writer.write(""","opdsServers":""")
            writer.write(json.encodeToString(if (options.opdsServers) createOpdsBackup() else emptyList()))
            writer.write(""","feedSources":""")
            writer.write(json.encodeToString(if (options.feed) createFeedSourceBackup() else emptyList()))
            writer.write(""","feedSavedSearches":""")
            writer.write(json.encodeToString(if (options.feed) createFeedSavedSearchBackup() else emptyList()))
            writer.write(""","trackerSyncStates":""")
            writer.write(json.encodeToString(if (options.effectiveTracking) createTrackerSyncStateBackup() else emptyList()))
            writer.write(""","syncConfigurations":""")
            writer.write(json.encodeToString(if (options.syncConfigurations) createSyncConfigBackup() else emptyList()))
            writer.write("}")
            writer.flush()
        }

    /**
     * Creates a full backup of all app data.
     * For large libraries, prefer [createBackupToStream] to avoid a large intermediate String.
     */
    suspend fun createBackup(options: BackupOptions = BackupOptions.ALL): String {
        val baos = java.io.ByteArrayOutputStream()
        createBackupToStream(baos, options)
        return baos.toString(Charsets.UTF_8.name())
    }

    /**
     * Creates backup data for all favorite manga with their chapters and reading history.
     */
    @Suppress("UnusedPrivateMember")
    private suspend fun createMangaBackup(): List<BackupManga> {
        // Get all favorite manga
        val favoriteManga = mangaDao.getFavoriteManga().first()

        // Load full reading history once and index by chapterId for fast lookups
        val historyByChapterId = readingHistoryDao.observeHistory().first()
            .associateBy { it.chapterId }

        return favoriteManga.map { mangaEntity ->
            // Get chapters for this manga
            val chapters = chapterDao.getChaptersByMangaId(mangaEntity.id).first()

            // Get reading history for each chapter
            val backupChapters = chapters.map { chapterEntity ->
                val history = historyByChapterId[chapterEntity.id]
                    ?.toBackupReadingHistory()

                chapterEntity.toBackupChapter(readingHistory = history)
            }

            // Get category associations for this manga
            val categoryIds = categoryDao.getCategoryIdsForManga(mangaEntity.id).first()

            mangaEntity.toBackupManga(
                chapters = backupChapters,
                categoryIds = categoryIds
            )
        }
    }

    /**
     * Creates backup data for all categories.
     */
    private suspend fun createCategoryBackup() =
        categoryDao.getCategories().first().map { it.toBackupCategory() }

    /**
     * Creates backup data for user preferences.
     */
    private suspend fun createPreferencesBackup() = createBackupPreferences(
        themeMode = generalPreferences.themeMode.first(),
        useDynamicColor = generalPreferences.useDynamicColor.first(),
        locale = generalPreferences.locale.first(),
        readerMode = readerPreferences.readerMode.first(),
        keepScreenOn = readerPreferences.keepScreenOn.first(),
        volumeKeysEnabled = readerPreferences.volumeKeysEnabled.first(),
        volumeKeysInverted = readerPreferences.volumeKeysInverted.first(),
        libraryGridSize = libraryPreferences.gridSize.first(),
        showBadges = libraryPreferences.showBadges.first(),
        updateCheckInterval = generalPreferences.updateCheckInterval.first(),
        notificationsEnabled = generalPreferences.notificationsEnabled.first()
    )

    private suspend fun createOpdsBackup() =
        opdsServerDao.getAll().first().map { it.toBackupOpdsServer() }

    private suspend fun createFeedSourceBackup() =
        feedDao.getFeedSources().first().map { it.toBackupFeedSource() }

    private suspend fun createFeedSavedSearchBackup() =
        feedDao.getSavedSearches().first().map { it.toBackupFeedSavedSearch() }

    private suspend fun createTrackerSyncStateBackup() =
        trackerSyncDao.getAllSyncStates().first().map { it.toBackupTrackerSyncState() }

    private suspend fun createSyncConfigBackup() =
        trackerSyncDao.getSyncConfigurations().first().map { it.toBackupSyncConfiguration() }
}
