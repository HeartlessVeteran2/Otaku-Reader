package app.otakureader.data.backup

import androidx.room.withTransaction
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.dao.CategoryDao
import app.otakureader.core.database.dao.ChapterDao
import app.otakureader.core.database.dao.FeedDao
import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.MangaCategoryDao
import app.otakureader.core.database.dao.OpdsServerDao
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.database.dao.TrackEntryDao
import app.otakureader.core.database.dao.TrackerSyncDao
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.data.backup.mapper.toCategoryEntity
import app.otakureader.data.backup.mapper.toChapterEntity
import app.otakureader.data.backup.mapper.toFeedSavedSearchEntity
import app.otakureader.data.backup.mapper.toFeedSourceEntity
import app.otakureader.data.backup.mapper.toMangaEntity
import app.otakureader.data.backup.mapper.toOpdsServerEntity
import app.otakureader.data.backup.mapper.toSyncConfigurationEntity
import app.otakureader.data.backup.mapper.toTrackEntryEntity
import app.otakureader.data.backup.mapper.toTrackerSyncStateEntity
import app.otakureader.data.backup.model.BackupData
import app.otakureader.domain.model.BackupOptions
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Restores backup data by parsing JSON and inserting data back into Room database.
 */
class BackupRestorer @Inject constructor(
    private val database: OtakuReaderDatabase,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val categoryDao: CategoryDao,
    private val mangaCategoryDao: MangaCategoryDao,
    private val readingHistoryDao: ReadingHistoryDao,
    private val trackEntryDao: TrackEntryDao,
    private val trackerSyncDao: TrackerSyncDao,
    private val opdsServerDao: OpdsServerDao,
    private val feedDao: FeedDao,
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
     *
     * All database writes are wrapped in a single Room transaction so that a
     * partial restore (e.g. process death mid-way) leaves the database unchanged.
     * Preference restoration runs after the transaction because DataStore cannot
     * participate in a Room transaction.
     *
     * @param backupJson JSON string containing the backup data
     * @param options Which data categories to restore. Defaults to everything (pre-#1192-PR-7
     *   behavior). Sections outside [options] are skipped even if present in the backup file.
     * @throws Exception if the backup cannot be parsed or restored
     */
    suspend fun restoreBackup(backupJson: String, options: BackupOptions = BackupOptions.ALL) {
        val backupData = json.decodeFromString<BackupData>(backupJson)

        database.withTransaction {
            if (options.categories) restoreCategories(backupData)
            if (options.libraryEntries) restoreManga(backupData, options)
            if (options.opdsServers) restoreOpdsServers(backupData)
            if (options.feed) {
                restoreFeedSources(backupData)
                restoreFeedSavedSearches(backupData)
            }
            if (options.syncConfigurations) restoreSyncConfigurations(backupData)
            if (options.effectiveTracking) restoreLegacyTrackerSyncStates(backupData)
        }

        if (options.preferences) restorePreferences(backupData)
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
    private suspend fun restoreManga(backupData: BackupData, options: BackupOptions) {
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
                mangaDao.insertOrGetExisting(backupManga.toMangaEntity())
            }

            // Restore chapters for this manga
            if (options.chapters) restoreChapters(mangaId, backupManga)

            // Restore category associations
            if (options.categories) restoreMangaCategories(mangaId, backupManga.categoryIds)

            // Restore tracker links and their sync bookkeeping. Nested in the manga, so the id
            // is the one this device just assigned rather than the source device's.
            if (options.effectiveTracking) restoreTracking(mangaId, backupManga)
        }
    }

    /**
     * Restores chapters and reading history for a manga.
     *
     * Wraps both chapter insert/update and reading history restore in a single database transaction
     * to ensure atomic operation. This prevents partial state if the process is interrupted between
     * the chapter operation and the history restore.
     */
    private suspend fun restoreChapters(mangaId: Long, backupManga: app.otakureader.data.backup.model.BackupManga) {
        // Fetch the manga's existing chapters ONCE and index them by URL. The previous
        // version re-queried the full chapter list inside the per-chapter loop, making the
        // restore O(chapters²) — noticeably slow for long series. The whole restore already
        // runs inside restoreBackup's withTransaction, so no inserts can sneak in between.
        // getChaptersByMangaIdOnce is a direct suspend query (not a Flow), so it is safe
        // to call here even though restoreChapters runs inside an outer withTransaction.
        // Calling Flow.first() inside a transaction can deadlock because the Flow dispatcher
        // tries to acquire a new database connection that the transaction already holds.
        val existingByUrl = chapterDao.getChaptersByMangaIdOnce(mangaId)
            .associateBy { it.url }
            .toMutableMap()

        backupManga.chapters.forEach { backupChapter ->
            // Wrap chapter and history restore in a single transaction to maintain atomicity
            database.withTransaction {
                val existingChapter = existingByUrl[backupChapter.url]

                val chapterId = if (existingChapter != null) {
                    // Update existing chapter
                    val updatedChapter = backupChapter.toChapterEntity(mangaId).copy(id = existingChapter.id)
                    chapterDao.update(updatedChapter)
                    existingChapter.id
                } else {
                    // Insert new chapter and remember it, so a duplicate URL later in the
                    // same backup updates this row instead of inserting a second copy.
                    val newId = chapterDao.upsert(backupChapter.toChapterEntity(mangaId))
                    existingByUrl[backupChapter.url] = backupChapter.toChapterEntity(mangaId).copy(id = newId)
                    newId
                }

                // Restore reading history if present.
                // replaceHistory uses an UPDATE-then-INSERT pattern to set the exact backed-up values
                // without accumulating duration on repeated restores. This avoids the DELETE-trigger
                // side-effects that raw INSERT OR REPLACE would cause on the auto-generated primary key.
                backupChapter.readingHistory?.let { history ->
                    readingHistoryDao.replaceHistory(chapterId, history.readAt, history.readDurationMs)
                }
            }
        }
    }

    /**
     * Restores one manga's tracker links and sync bookkeeping (#1271).
     *
     * The links are the part that matters. `track_entries` was never written to a backup at all —
     * [BackupCreator] had no [TrackEntryDao] — so before v5 every MyAnimeList, AniList, Kitsu,
     * MangaUpdates and Shikimori link in the library was silently lost on restore, and the details
     * screen came back with no tracker chips. The sync state is bookkeeping *about* those links,
     * which is why restoring it without them was backwards even where the ids happened to line up.
     *
     * Both are keyed here by [mangaId] — the id this device assigned moments ago in [restoreManga]
     * — because a [app.otakureader.data.backup.model.BackupTrackEntry] carries no manga id of its
     * own. What makes the link portable is `remoteId`: the tracker's identifier for the series,
     * which means the same thing on every device.
     */
    private suspend fun restoreTracking(mangaId: Long, backupManga: app.otakureader.data.backup.model.BackupManga) {
        backupManga.trackEntries.forEach { entry ->
            trackEntryDao.upsert(entry.toTrackEntryEntity(mangaId))
        }
        if (backupManga.trackerSyncStates.isNotEmpty()) {
            trackerSyncDao.insertSyncStates(
                backupManga.trackerSyncStates.map { it.toTrackerSyncStateEntity(mangaId) }
            )
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

    private suspend fun restoreOpdsServers(backupData: BackupData) {
        if (backupData.opdsServers.isEmpty()) return
        opdsServerDao.insertAll(backupData.opdsServers.map { it.toOpdsServerEntity() })
    }

    private suspend fun restoreFeedSources(backupData: BackupData) {
        if (backupData.feedSources.isEmpty()) return
        feedDao.insertFeedSources(backupData.feedSources.map { it.toFeedSourceEntity() })
    }

    private suspend fun restoreFeedSavedSearches(backupData: BackupData) {
        if (backupData.feedSavedSearches.isEmpty()) return
        feedDao.insertSavedSearches(backupData.feedSavedSearches.map { it.toFeedSavedSearchEntity() })
    }

    private suspend fun restoreSyncConfigurations(backupData: BackupData) {
        if (backupData.syncConfigurations.isEmpty()) return
        trackerSyncDao.insertSyncConfigurations(backupData.syncConfigurations.map { it.toSyncConfigurationEntity() })
    }

    /**
     * Restores the flat, top-level tracker sync list that v4 and older backups carry (#1271).
     *
     * v5 nests this data inside each manga, where the manga is identified by `(sourceId, url)` like
     * every other per-manga section, and [restoreTracking] handles it. This path exists only so a
     * v4 file is not silently worse off than before — and it can do very little, which is the whole
     * reason the format changed.
     *
     * `BackupTrackerSyncState.legacyMangaId` is the *source device's* local row id.
     * [app.otakureader.data.backup.model.BackupManga] carries no id, and [restoreManga] assigns
     * fresh autogenerated ids here, so the two only line up by luck: reliably on a restore into an
     * empty library, where both sides number from 1 in the same order, and not at all once the
     * destination already holds entries. The filter keeps the lucky rows and drops the rest.
     *
     * Dropping them is required, not defensive. `tracker_sync_state.mangaId` has a cascading
     * foreign key to `manga` (#1248), so an unresolvable row would violate it and abort the entire
     * restore transaction. Before that foreign key existed they were inserted regardless, pointing
     * at whatever manga happened to hold the id — so this removes rows that were never meaningful
     * rather than losing anything the user had.
     *
     * Note what even a lucky v4 row cannot give back: the tracker *links* themselves. Those live in
     * `track_entries`, which no backup before v5 ever wrote.
     */
    private suspend fun restoreLegacyTrackerSyncStates(backupData: BackupData) {
        if (backupData.legacyTrackerSyncStates.isEmpty()) return
        val knownMangaIds = mangaDao.getAllMangaOnce().mapTo(mutableSetOf()) { it.id }
        val restorable = backupData.legacyTrackerSyncStates
            .filter { it.legacyMangaId in knownMangaIds }
            .map { it.toTrackerSyncStateEntity(it.legacyMangaId) }
        if (restorable.isEmpty()) return
        trackerSyncDao.insertSyncStates(restorable)
    }
}
