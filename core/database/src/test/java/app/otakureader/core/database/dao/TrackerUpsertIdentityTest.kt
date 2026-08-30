package app.otakureader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otakureader.core.database.OtakuReaderDatabase
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.SyncConfigurationEntity
import app.otakureader.core.database.entity.TrackEntryEntity
import app.otakureader.core.database.entity.TrackerSyncStateEntity
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The three remaining tables that used `OnConflictStrategy.REPLACE` against a *secondary* unique
 * index (#1276), and the property that fixing them buys: **a row keeps its primary key**.
 *
 * REPLACE deletes the conflicting row before inserting, so an `autoGenerate` key is reassigned on
 * every write. That is the same defect that destroyed data in `ChapterDao` (#1254) and `MangaDao`
 * (#1269); it was survivable on these three only because nothing holds a foreign key to them.
 *
 * `tracker_sync_state` is the one where it was closest to biting: `updateSyncStatus`,
 * `markSyncSuccess` and `markConflict` all address a row by id, and callers hold that id across a
 * network round-trip.
 *
 * Each test asserts the id **and** that the new values landed — an upsert that preserved the id by
 * doing nothing would satisfy half of this.
 */
@RunWith(AndroidJUnit4::class)
class TrackerUpsertIdentityTest {

    private lateinit var database: OtakuReaderDatabase
    private var mangaId = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtakuReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        // Both tracker tables cascade from `manga` (#1248), so a parent row has to exist.
        mangaId = database.mangaDao().insertOrGetExisting(
            MangaEntity(sourceId = 1L, url = "/m/1", title = "Vinland Saga"),
        )
    }

    @After
    fun tearDown() = database.close()

    private fun trackEntry(lastChapterRead: Float, score: Float) = TrackEntryEntity(
        mangaId = mangaId, trackerId = 1, remoteId = 44347L, title = "Vinland Saga",
        status = 1, lastChapterRead = lastChapterRead, totalChapters = 214, score = score,
        startDate = 0, finishDate = 0,
    )

    private fun syncState(localLastChapterRead: Float) = TrackerSyncStateEntity(
        mangaId = mangaId, trackerId = 1, remoteId = "44347",
        localLastChapterRead = localLastChapterRead, localTotalChapters = 214, localStatus = 0,
        localLastModified = Instant.ofEpochMilli(1_000L),
        remoteLastChapterRead = localLastChapterRead, remoteTotalChapters = 214, remoteStatus = 0,
        remoteLastModified = null, syncStatus = 0,
        lastSyncAttempt = null, lastSuccessfulSync = null, syncError = null,
    )

    @Test
    fun `re-upserting a track entry keeps its id and applies the new values`() = runBlocking {
        val dao = database.trackEntryDao()
        val first = dao.upsert(trackEntry(lastChapterRead = 10f, score = 7f))

        val second = dao.upsert(trackEntry(lastChapterRead = 42f, score = 9.5f))

        assertEquals("the row must keep its primary key across a sync", first, second)
        val rows = dao.getByMangaId(mangaId).first()
        assertEquals("no duplicate row", 1, rows.size)
        assertEquals(first, rows.single().id)
        assertEquals(42f, rows.single().lastChapterRead, 0f)
        assertEquals(9.5f, rows.single().score, 0f)
    }

    /**
     * `insertSyncState` is **create-if-absent**, and an existing row must survive untouched.
     *
     * Its only caller is the auto-create branch in `syncManga`, which reads "no row yet" and then
     * inserts, holding nothing across the two steps. Two syncs for the same manga and tracker can
     * both see null; if the loser overwrote, it would replace the winner's row with its own older
     * snapshot. The id assertion and the value assertion cover different halves of that — a
     * REPLACE would break the first, and an overwrite-style upsert would break the second.
     */
    @Test
    fun `re-inserting a sync state keeps the id and does not clobber the existing row`() = runBlocking {
        val dao = database.trackerSyncDao()
        val first = dao.insertSyncState(syncState(localLastChapterRead = 10f))

        val second = dao.insertSyncState(syncState(localLastChapterRead = 42f))

        assertEquals("markSyncSuccess addresses this row by id", first, second)
        val rows = dao.getAllSyncStates().first()
        assertEquals(1, rows.size)
        assertEquals(first, rows.single().id)
        assertEquals(
            "a second create must not overwrite the row that already exists",
            10f,
            rows.single().localLastChapterRead,
            0f,
        )
    }

    /** The bulk path a restore uses: the backup still wins, without reassigning ids. */
    @Test
    fun `bulk sync-state insert overwrites in place`() = runBlocking {
        val dao = database.trackerSyncDao()
        val first = dao.insertSyncState(syncState(localLastChapterRead = 1f))

        dao.insertSyncStates(listOf(syncState(localLastChapterRead = 99f)))

        val rows = dao.getAllSyncStates().first()
        assertEquals("a restore must not duplicate the row", 1, rows.size)
        assertEquals(first, rows.single().id)
        assertEquals("the backup's value must win", 99f, rows.single().localLastChapterRead, 0f)
    }

    @Test
    fun `re-inserting a sync configuration keeps its id and applies the new values`() = runBlocking {
        val dao = database.trackerSyncDao()
        val first = dao.insertSyncConfiguration(SyncConfigurationEntity(trackerId = 1, enabled = true, syncDirection = 0, conflictResolution = 0))

        val second = dao.insertSyncConfiguration(SyncConfigurationEntity(trackerId = 1, enabled = false, syncDirection = 0, conflictResolution = 0))

        assertEquals(first, second)
        val rows = dao.getSyncConfigurations().first()
        assertEquals(1, rows.size)
        assertEquals(false, rows.single().enabled)
    }

    /** A genuinely different pair still gets its own row. */
    @Test
    fun `a different tracker inserts a new row`() = runBlocking {
        val dao = database.trackEntryDao()
        val first = dao.upsert(trackEntry(lastChapterRead = 1f, score = 1f))

        val second = dao.upsert(trackEntry(lastChapterRead = 1f, score = 1f).copy(trackerId = 2))

        assert(first != second) { "distinct (manga, tracker) pairs must not share a row" }
        assertEquals(2, dao.getByMangaId(mangaId).first().size)
    }
}
