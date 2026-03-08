package app.otakureader.data.backup

import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.data.backup.mapper.createBackupPreferences
import app.otakureader.data.backup.mapper.toBackupCategory
import app.otakureader.data.backup.mapper.toBackupChapter
import app.otakureader.data.backup.mapper.toBackupManga
import app.otakureader.data.backup.mapper.toBackupReadingHistory
import app.otakureader.data.backup.model.BackupData
import app.otakureader.data.backup.model.BackupManga
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Creates backup data by serializing library, categories, history, and preferences to JSON.
 */
class BackupCreator @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val categoryDao: CategoryDao,
    private val readingHistoryDao: ReadingHistoryDao,
    private val generalPreferences: GeneralPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Creates a full backup of all app data.
     * @return JSON string containing the backup data
     */
    suspend fun createBackup(): String {
        val backupData = BackupData(
            manga = createMangaBackup(),
            categories = createCategoryBackup(),
            preferences = createPreferencesBackup()
        )

        return json.encodeToString(backupData)
    }

    /**
     * Creates backup data for all favorite manga with their chapters and reading history.
     */
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
        tapZonesEnabled = readerPreferences.tapZonesEnabled.first(),
        libraryGridSize = libraryPreferences.gridSize.first(),
        showBadges = libraryPreferences.showBadges.first(),
        updateCheckInterval = generalPreferences.updateCheckInterval.first(),
        notificationsEnabled = generalPreferences.notificationsEnabled.first()
    )
}
