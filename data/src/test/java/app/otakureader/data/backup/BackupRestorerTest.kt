package app.otakureader.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.MangaEntity
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
import app.otakureader.data.backup.model.BackupTrackEntry
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
import org.junit.Assert.assertNotEquals
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
            trackEntryDao = database.trackEntryDao(),
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
                trackEntries = listOf(
                    BackupTrackEntry(
                        trackerId = 1, remoteId = 44347L,
                        remoteUrl = "https://myanimelist.net/manga/44347", title = "Test Manga",
                        status = 1, lastChapterRead = 12f, totalChapters = 100, score = 8.5f,
                        startDate = 1_000L, finishDate = 0L,
                    )
                ),
                trackerSyncStates = listOf(
                    BackupTrackerSyncState(
                        trackerId = 1, remoteId = "44347",
                        localLastChapterRead = 12f, localTotalChapters = 100, localStatus = 0,
                        localLastModifiedEpochMilli = 5_000L, remoteLastChapterRead = 12f,
                        remoteTotalChapters = 100, remoteStatus = 0, syncStatus = 1,
                    )
                ),
            )
        ),
        categories = listOf(BackupCategory(id = 1L, name = "Reading")),
        preferences = BackupPreferences(),
        opdsServers = listOf(BackupOpdsServer(id = 1L, name = "Server", url = "https://example.com")),
        feedSources = listOf(BackupFeedSource(id = 1L, sourceId = 1L, sourceName = "Source")),
        feedSavedSearches = listOf(BackupFeedSavedSearch(id = 1L, sourceId = 1L, sourceName = "Source", query = "q")),
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
    fun `tracking off skips tracker restore even with libraryEntries on`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL.copy(tracking = false))

        val manga = database.mangaDao().getFavoriteManga().first().single()
        assertTrue(database.trackEntryDao().getByMangaId(manga.id).first().isEmpty())
        assertTrue(database.trackerSyncDao().getAllSyncStates().first().isEmpty())
    }

    /**
     * The bug #1271 was opened for. `track_entries` was never written to a backup, so a restore
     * came back with every MyAnimeList/AniList/Kitsu link gone and no tracker chips on the details
     * screen. This asserts the link lands attached to the manga the restore actually created —
     * which is the part the old flat format could not do.
     */
    @Test
    fun `tracker links are restored and attached to the restored manga`() = runBlocking {
        restorer.restoreBackup(backupJson(), BackupOptions.ALL)

        val manga = database.mangaDao().getFavoriteManga().first().single()
        val entry = database.trackEntryDao().getByMangaId(manga.id).first().single()
        assertEquals(1, entry.trackerId)
        assertEquals(44347L, entry.remoteId)
        assertEquals(12f, entry.lastChapterRead, 0f)
        assertEquals(8.5f, entry.score, 0f)

        val syncState = database.trackerSyncDao().getAllSyncStates().first().single()
        assertEquals(
            "sync state must point at the id this device assigned, not the backup's",
            manga.id,
            syncState.mangaId,
        )
        assertEquals("44347", syncState.remoteId)
    }

    /**
     * The case the old flat format got wrong, and the reason nesting was the fix: the destination
     * already holds a manga, so ids no longer line up between the two devices. Restoring here used
     * to attach the tracker row to whatever manga happened to hold id 1 — or, once the #1248
     * foreign key landed, drop it.
     */
    @Test
    fun `tracker data survives a restore into a library that already has entries`() = runBlocking {
        database.mangaDao().insertOrGetExisting(
            MangaEntity(sourceId = 99L, url = "/other/1", title = "Pre-existing", favorite = true)
        )

        restorer.restoreBackup(backupJson(), BackupOptions.ALL)

        val restored = database.mangaDao().getMangaBySourceAndUrl(1L, "/m/1")!!
        assertNotEquals("the ids must actually differ for this test to bite", 1L, restored.id)
        assertEquals(1, database.trackEntryDao().getByMangaId(restored.id).first().size)
        assertEquals(restored.id, database.trackerSyncDao().getAllSyncStates().first().single().mangaId)
    }

    /**
     * A v4 file still parses and still restores what it can. Its tracker sync rows are keyed by the
     * source device's local manga id, so they only resolve when the ids happen to line up — here
     * they do, restoring into an empty library. The tracker *links* are unrecoverable either way:
     * no backup before v5 contained them.
     */
    @Test
    fun `a v4 backup still restores its top-level tracker sync rows where the ids line up`() = runBlocking {
        val v4 = """
            {"version":4,"createdAt":0,
             "manga":[{"sourceId":1,"url":"/m/1","title":"Test Manga","favorite":true}],
             "categories":[],"preferences":null,"opdsServers":[],"feedSources":[],
             "feedSavedSearches":[],
             "trackerSyncStates":[{"mangaId":1,"trackerId":1,"remoteId":"r1",
               "localLastChapterRead":1.0,"localTotalChapters":10,"localStatus":0,
               "localLastModifiedEpochMilli":0,"remoteLastChapterRead":1.0,
               "remoteTotalChapters":10,"remoteStatus":0,"syncStatus":0}],
             "syncConfigurations":[]}
        """.trimIndent()

        restorer.restoreBackup(v4, BackupOptions.ALL)

        val manga = database.mangaDao().getFavoriteManga().first().single()
        assertEquals(1L, manga.id)
        assertEquals(1, database.trackerSyncDao().getAllSyncStates().first().size)
        assertTrue(
            "v4 carried no tracker links at all, so none can come back",
            database.trackEntryDao().getByMangaId(manga.id).first().isEmpty(),
        )
    }
}
