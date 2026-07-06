package app.otakureader.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.data.backup.model.BackupCategory
import app.otakureader.data.backup.model.BackupChapter
import app.otakureader.data.backup.model.BackupData
import app.otakureader.data.backup.model.BackupFeedSavedSearch
import app.otakureader.data.backup.model.BackupFeedSource
import app.otakureader.data.backup.model.BackupManga
import app.otakureader.data.backup.model.BackupOpdsServer
import app.otakureader.data.backup.model.BackupPreferences
import app.otakureader.data.backup.model.BackupSyncConfiguration
import app.otakureader.data.backup.model.BackupTrackerSyncState
import app.otakureader.domain.model.BackupOptions
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round-trip tests for [BackupRestorer] with [BackupOptions] sections toggled off — verifies
 * each restore step is skipped when its section is deselected, even when the backup JSON
 * still contains data for it, per #1192 PR 7.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestorerTest {

    private lateinit var database: OtakuReaderDatabase
    private lateinit var restorer: BackupRestorer
    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var readerPreferences: ReaderPreferences
    private val json = Json { encodeDefaults = true }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java
        ).allowMainThreadQueries().build()

        generalPreferences = mockk(relaxed = true)
        libraryPreferences = mockk(relaxed = true)
        readerPreferences = mockk(relaxed = true)

        restorer = BackupRestorer(
            database = database,
            mangaDao = database.mangaDao(),
            chapterDao = database.chapterDao(),
            categoryDao = database.categoryDao(),
            mangaCategoryDao = database.mangaCategoryDao(),
            readingHistoryDao = database.readingHistoryDao(),
            trackerSyncDao = database.trackerSyncDao(),
            opdsServerDao = database.opdsServerDao(),
            feedDao = database.feedDao(),
            generalPreferences = generalPreferences,
            libraryPreferences = libraryPreferences,
            readerPreferences = readerPreferences,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private val fullBackup = BackupData(
        manga = listOf(
            BackupManga(
                sourceId = 1L,
                url = "/m/1",
                title = "Test Manga",
                favorite = true,
                chapters = listOf(
                    BackupChapter(url = "/c/1", name = "Chapter 1", chapterNumber = 1f)
                ),
                categoryIds = listOf(1L),
            )
        ),
        categories = listOf(BackupCategory(id = 1L, name = "Reading")),
        preferences = BackupPreferences(),
        opdsServers = listOf(BackupOpdsServer(id = 1L, name = "Server", url = "https://example.com")),
        feedSources = listOf(BackupFeedSource(id = 1L, sourceId = 1L, sourceName = "Source")),
        feedSavedSearches = listOf(BackupFeedSavedSearch(id = 1L, sourceId = 1L, sourceName = "Source", query = "q")),
        trackerSyncStates = listOf(
            BackupTrackerSyncState(
                mangaId = 1L, trackerId = 1, remoteId = "r1",
                localLastChapterRead = 1f, localTotalChapters = 10, localStatus = 0,
                localLastModifiedEpochMilli = 0L, remoteLastChapterRead = 1f,
                remoteTotalChapters = 10, remoteStatus = 0, syncStatus = 0,
            )
        ),
        syncConfigurations = listOf(BackupSyncConfiguration(trackerId = 1, syncDirection = 0, conflictResolution = 0)),
    )

    private fun backupJson(data: BackupData = fullBackup) = json.encodeToString(data)

    @Test
    fun `default options restores every section`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL)

        assertEquals(1, database.mangaDao().getFavoriteManga().first().size)
        assertEquals(1, database.categoryDao().getCategories().first().size)
        assertEquals(1, database.opdsServerDao().getAll().first().size)
        assertEquals(1, database.feedDao().getFeedSources().first().size)
        assertEquals(1, database.feedDao().getSavedSearches().first().size)
        assertEquals(1, database.trackerSyncDao().getSyncConfigurations().first().size)
        assertEquals(1, database.trackerSyncDao().getAllSyncStates().first().size)
    }

    @Test
    fun `libraryEntries off skips manga restore entirely`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL.copy(libraryEntries = false))

        assertTrue(database.mangaDao().getFavoriteManga().first().isEmpty())
    }

    @Test
    fun `chapters off restores manga but not its chapters`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL.copy(chapters = false))

        val manga = database.mangaDao().getFavoriteManga().first().single()
        assertTrue(database.chapterDao().getChaptersByMangaIdOnce(manga.id).isEmpty())
    }

    @Test
    fun `categories off restores manga without category rows or associations`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL.copy(categories = false))

        assertTrue(database.categoryDao().getCategories().first().isEmpty())
        val manga = database.mangaDao().getFavoriteManga().first().single()
        assertTrue(database.categoryDao().getCategoryIdsForManga(manga.id).first().isEmpty())
    }

    @Test
    fun `preferences off never calls any preference setter`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL.copy(preferences = false))

        coVerify(exactly = 0) { generalPreferences.setThemeMode(any()) }
        coVerify(exactly = 0) { libraryPreferences.setGridSize(any()) }
        coVerify(exactly = 0) { readerPreferences.setReaderMode(any()) }
    }

    @Test
    fun `preferences on calls the preference setters`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL)

        coVerify(exactly = 1) { generalPreferences.setThemeMode(any()) }
    }

    @Test
    fun `opdsServers off skips opds server restore`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL.copy(opdsServers = false))

        assertTrue(database.opdsServerDao().getAll().first().isEmpty())
    }

    @Test
    fun `feed off skips both feed sources and saved searches`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL.copy(feed = false))

        assertTrue(database.feedDao().getFeedSources().first().isEmpty())
        assertTrue(database.feedDao().getSavedSearches().first().isEmpty())
    }

    @Test
    fun `syncConfigurations off skips sync configuration restore`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL.copy(syncConfigurations = false))

        assertTrue(database.trackerSyncDao().getSyncConfigurations().first().isEmpty())
    }

    @Test
    fun `tracking off skips tracker sync state restore even with libraryEntries on`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL.copy(tracking = false))

        assertTrue(database.trackerSyncDao().getAllSyncStates().first().isEmpty())
    }
}
