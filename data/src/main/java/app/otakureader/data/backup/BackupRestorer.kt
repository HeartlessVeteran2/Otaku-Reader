package app.otakureader.data.backup

import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.MangaCategoryDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.data.backup.mapper.toCategoryEntity
import app.otakureader.data.backup.mapper.toChapterEntity
import app.otakureader.data.backup.mapper.toMangaEntity
import app.otakureader.data.backup.mapper.toReadingHistoryEntity
import app.otakureader.data.backup.model.BackupData
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Restores backup data by parsing JSON and inserting data back into Room database.
 */
class BackupRestorer @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val categoryDao: CategoryDao,
    private val mangaCategoryDao: MangaCategoryDao,
    private val readingHistoryDao: ReadingHistoryDao,
    private val generalPreferences: GeneralPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Restores all data from a backup JSON string.
     * @param backupJson JSON string containing the backup data
     * @throws Exception if the backup cannot be parsed or restored
     */
    suspend fun restoreBackup(backupJson: String) {
        val backupData = json.decodeFromString<BackupData>(backupJson)

        // Restore in order: categories first, then manga with chapters
        restoreCategories(backupData)
        restoreManga(backupData)
        restorePreferences(backupData)
    }

    /**
     * Restores categories from backup.
     * Uses REPLACE strategy to handle conflicts.
     */
    private suspend fun restoreCategories(backupData: BackupData) {
        backupData.categories.forEach { backupCategory ->
            val categoryEntity = backupCategory.toCategoryEntity()
            categoryDao.insert(categoryEntity)
        }
    }

    /**
     * Restores manga, chapters, and reading history from backup.
     */
    private suspend fun restoreManga(backupData: BackupData) {
        backupData.manga.forEach { backupManga ->
            // Check if manga already exists by source and URL
            val existingManga = mangaDao.getMangaBySourceAndUrl(
                backupManga.sourceId,
                backupManga.url
            )

            val mangaId = if (existingManga != null) {
                // Update existing manga
                val updatedManga = backupManga.toMangaEntity().copy(id = existingManga.id)
                mangaDao.update(updatedManga)
                existingManga.id
            } else {
                // Insert new manga
                mangaDao.insert(backupManga.toMangaEntity())
            }

            // Restore chapters for this manga
            restoreChapters(mangaId, backupManga)

            // Restore category associations
            restoreMangaCategories(mangaId, backupManga.categoryIds)
        }
    }

    /**
     * Restores chapters and reading history for a manga.
     */
    private suspend fun restoreChapters(mangaId: Long, backupManga: app.otakureader.data.backup.model.BackupManga) {
        backupManga.chapters.forEach { backupChapter ->
            // Check if chapter already exists by URL
            val existingChapters = chapterDao.getChaptersByMangaId(mangaId).first()
            val existingChapter = existingChapters.find { it.url == backupChapter.url }

            val chapterId = if (existingChapter != null) {
                // Update existing chapter
                val updatedChapter = backupChapter.toChapterEntity(mangaId).copy(id = existingChapter.id)
                chapterDao.update(updatedChapter)
                existingChapter.id
            } else {
                // Insert new chapter
                chapterDao.insert(backupChapter.toChapterEntity(mangaId))
            }

            // Restore reading history if present
            backupChapter.readingHistory?.let { history ->
                readingHistoryDao.upsert(history.toReadingHistoryEntity(chapterId))
            }
        }
    }

    /**
     * Restores manga-category associations.
     */
    private suspend fun restoreMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        // Clear existing associations for this manga
        mangaCategoryDao.deleteAllForManga(mangaId)

        // Insert new associations
        categoryIds.forEach { categoryId ->
            mangaCategoryDao.upsert(
                MangaCategoryEntity(
                    mangaId = mangaId,
                    categoryId = categoryId
                )
            )
        }
    }

    /**
     * Restores user preferences from backup.
     */
    private suspend fun restorePreferences(backupData: BackupData) {
        val prefs = backupData.preferences ?: return

        // General preferences
        generalPreferences.setThemeMode(prefs.themeMode)
        generalPreferences.setUseDynamicColor(prefs.useDynamicColor)
        generalPreferences.setLocale(prefs.locale)
        generalPreferences.setNotificationsEnabled(prefs.notificationsEnabled)
        generalPreferences.setUpdateCheckInterval(prefs.updateCheckInterval)

        // Library preferences
        libraryPreferences.setGridSize(prefs.libraryGridSize)
        libraryPreferences.setShowBadges(prefs.showBadges)

        // Reader preferences
        readerPreferences.setReaderMode(prefs.readerMode)
        readerPreferences.setKeepScreenOn(prefs.keepScreenOn)
        readerPreferences.setVolumeKeysEnabled(prefs.volumeKeysEnabled)
        readerPreferences.setVolumeKeysInverted(prefs.volumeKeysInverted)
    }
}
