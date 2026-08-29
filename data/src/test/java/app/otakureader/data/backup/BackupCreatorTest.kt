package app.otakureader.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.CategoryEntity
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.MangaCategoryEntity
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.OpdsServerEntity
import app.otakureader.core.database.entity.TrackEntryEntity
import app.otakureader.core.database.entity.TrackerSyncStateEntity
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.data.backup.model.BackupData
import app.otakureader.domain.model.BackupOptions
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * Round-trip tests for [BackupCreator] with [BackupOptions] sections toggled off — verifies
 * deselected sections are written as empty arrays (or null for preferences) rather than
 * omitted, per #1192 PR 7.
 */
@RunWith(AndroidJUnit4::class)
class BackupCreatorTest {

    private lateinit var database: OtakuReaderDatabase
    private lateinit var creator: BackupCreator
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java
        ).allowMainThreadQueries().build()

        val generalPreferences = mockk<GeneralPreferences>(relaxed = true) {
            every { themeMode } returns flowOf(0)
            every { useDynamicColor } returns flowOf(true)
            every { locale } returns flowOf("")
            every { updateCheckInterval } returns flowOf(12)
            every { notificationsEnabled } returns flowOf(true)
        }
        val libraryPreferences = mockk<LibraryPreferences>(relaxed = true) {
            every { gridSize } returns flowOf(3)
            every { showBadges } returns flowOf(true)
        }
        val readerPreferences = mockk<ReaderPreferences>(relaxed = true) {
            every { readerMode } returns flowOf(0)
            every { keepScreenOn } returns flowOf(true)
            every { volumeKeysEnabled } returns flowOf(false)
            every { volumeKeysInverted } returns flowOf(false)
        }

        creator = BackupCreator(
            mangaDao = database.mangaDao(),
            chapterDao = database.chapterDao(),
            categoryDao = database.categoryDao(),
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

    private suspend fun seedFavoriteMangaWithChapterAndCategory(): Long {
        val mangaId = database.mangaDao().insertOrGetExisting(
            MangaEntity(sourceId = 1L, url = "/m/1", title = "Test Manga", favorite = true)
        )
        database.chapterDao().upsert(
            ChapterEntity(mangaId = mangaId, url = "/c/1", name = "Chapter 1", chapterNumber = 1f)
        )
        database.categoryDao().insert(CategoryEntity(id = 1L, name = "Reading", order = 0))
        database.mangaCategoryDao().upsert(MangaCategoryEntity(mangaId = mangaId, categoryId = 1L))
        return mangaId
    }

    /** A tracker link plus the sync bookkeeping about it — the two halves #1271 is concerned with. */
    private suspend fun seedTracking(mangaId: Long) {
        database.trackEntryDao().upsert(
            TrackEntryEntity(
                mangaId = mangaId, trackerId = 1, remoteId = 44347L,
                remoteUrl = "https://myanimelist.net/manga/44347", title = "Test Manga",
                status = 1, lastChapterRead = 12f, totalChapters = 100, score = 8.5f,
                startDate = 1_000L, finishDate = 0L,
            )
        )
        database.trackerSyncDao().insertSyncState(
            TrackerSyncStateEntity(
                mangaId = mangaId, trackerId = 1, remoteId = "44347",
                localLastChapterRead = 12f, localTotalChapters = 100, localStatus = 0,
                localLastModified = Instant.ofEpochMilli(5_000L),
                remoteLastChapterRead = 12f, remoteTotalChapters = 100, remoteStatus = 0,
                remoteLastModified = null, syncStatus = 1,
                lastSyncAttempt = null, lastSuccessfulSync = null, syncError = null,
            )
        )
    }

    private suspend fun createAndDecode(options: BackupOptions): BackupData {
        val output = ByteArrayOutputStream()
        creator.createBackupToStream(output, options)
        return json.decodeFromString(output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `default options include manga with its chapters and categories`() = runBlocking {
        seedFavoriteMangaWithChapterAndCategory()

        val backup = createAndDecode(BackupOptions.ALL)

        assertEquals(1, backup.manga.size)
        assertEquals(1, backup.manga[0].chapters.size)
        assertEquals(listOf(1L), backup.manga[0].categoryIds)
    }

    @Test
    fun `libraryEntries off emits an empty manga array`() = runBlocking {
        seedFavoriteMangaWithChapterAndCategory()

        val backup = createAndDecode(BackupOptions.ALL.copy(libraryEntries = false))

        assertTrue(backup.manga.isEmpty())
    }

    @Test
    fun `chapters off keeps the manga entry but empties its chapter list`() = runBlocking {
        seedFavoriteMangaWithChapterAndCategory()

        val backup = createAndDecode(BackupOptions.ALL.copy(chapters = false))

        assertEquals(1, backup.manga.size)
        assertTrue(backup.manga[0].chapters.isEmpty())
    }

    @Test
    fun `categories off empties both the categories list and manga categoryIds`() = runBlocking {
        seedFavoriteMangaWithChapterAndCategory()

        val backup = createAndDecode(BackupOptions.ALL.copy(categories = false))

        assertTrue(backup.categories.isEmpty())
        assertTrue(backup.manga[0].categoryIds.isEmpty())
    }

    @Test
    fun `preferences off writes null preferences`() = runBlocking {
        val backup = createAndDecode(BackupOptions.ALL.copy(preferences = false))

        assertNull(backup.preferences)
    }

    @Test
    fun `preferences on writes non-null preferences`() = runBlocking {
        val backup = createAndDecode(BackupOptions.ALL)

        assertNotNull(backup.preferences)
    }

    @Test
    fun `opdsServers off empties the opds server list`() = runBlocking {
        database.opdsServerDao().insertAll(listOf(OpdsServerEntity(id = 1L, name = "Server", url = "https://example.com")))

        val backup = createAndDecode(BackupOptions.ALL.copy(opdsServers = false))

        assertTrue(backup.opdsServers.isEmpty())
    }

    @Test
    fun `opdsServers on includes opds servers`() = runBlocking {
        database.opdsServerDao().insertAll(listOf(OpdsServerEntity(id = 1L, name = "Server", url = "https://example.com")))

        val backup = createAndDecode(BackupOptions.ALL)

        assertEquals(1, backup.opdsServers.size)
    }

    /**
     * The gap #1271 was opened for: `track_entries` was never written to a backup at all, so a
     * restore came back with no tracker links and the details screen showed no chips.
     *
     * Asserts the field values, not just the count. `remoteId` is what makes the link portable —
     * it is the tracker's own identifier for the series and means the same thing on every device,
     * unlike the local row id the old format tried to carry.
     */
    @Test
    fun `tracker links are backed up, nested in their manga`() = runBlocking {
        val mangaId = seedFavoriteMangaWithChapterAndCategory()
        seedTracking(mangaId)

        val backup = createAndDecode(BackupOptions.ALL)

        val entry = backup.manga.single().trackEntries.single()
        assertEquals(1, entry.trackerId)
        assertEquals(44347L, entry.remoteId)
        assertEquals("https://myanimelist.net/manga/44347", entry.remoteUrl)
        assertEquals(12f, entry.lastChapterRead, 0f)
        assertEquals(8.5f, entry.score, 0f)
        assertEquals(100, entry.totalChapters)
    }

    /** Sync bookkeeping travels with its manga now, not in a flat list keyed by a local id. */
    @Test
    fun `tracker sync state is nested in its manga and no longer written at top level`() = runBlocking {
        val mangaId = seedFavoriteMangaWithChapterAndCategory()
        seedTracking(mangaId)

        val backup = createAndDecode(BackupOptions.ALL)

        assertEquals(1, backup.manga.single().trackerSyncStates.size)
        assertEquals("44347", backup.manga.single().trackerSyncStates.single().remoteId)
        assertTrue(
            "the v4 top-level list must be empty in a v5 backup",
            backup.legacyTrackerSyncStates.isEmpty(),
        )
    }

    /**
     * Seeds real tracker rows before turning the section off. The previous version of this test
     * seeded none, so it asserted an empty list against a database that had nothing to write
     * either way — it would have passed with the rule inverted (self-review item 3).
     */
    @Test
    fun `tracking off empties tracker data even with libraryEntries on`() = runBlocking {
        val mangaId = seedFavoriteMangaWithChapterAndCategory()
        seedTracking(mangaId)

        val backup = createAndDecode(BackupOptions.ALL.copy(tracking = false))

        assertTrue(backup.manga.single().trackEntries.isEmpty())
        assertTrue(backup.manga.single().trackerSyncStates.isEmpty())
    }

    /** Tracker data is per-manga, so it goes when the library section does. */
    @Test
    fun `libraryEntries off drops tracker data with the manga`() = runBlocking {
        val mangaId = seedFavoriteMangaWithChapterAndCategory()
        seedTracking(mangaId)

        val backup = createAndDecode(BackupOptions.ALL.copy(libraryEntries = false))

        assertTrue(backup.manga.isEmpty())
        assertTrue(backup.legacyTrackerSyncStates.isEmpty())
    }

    @Test
    fun `syncConfigurations off empties the syncConfigurations list`() = runBlocking {
        val backup = createAndDecode(BackupOptions.ALL.copy(syncConfigurations = false))

        assertTrue(backup.syncConfigurations.isEmpty())
    }
}
