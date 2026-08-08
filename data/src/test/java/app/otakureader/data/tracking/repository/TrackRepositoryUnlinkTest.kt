package app.otakureader.data.tracking.repository

import app.otakureader.core.database.dao.TrackEntryDao
import app.otakureader.core.database.dao.TrackerSyncDao
import app.otakureader.core.database.entity.TrackerSyncStateEntity
import app.otakureader.data.tracking.TrackManager
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.domain.tracking.Tracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unlinking a tracker has to take the sync-state row with it.
 *
 * `tracker_sync_state` holds no foreign key to `track_entries`, so nothing at the database level
 * ties their lifetimes together, and `deleteSyncStateForManga` had no production caller at all.
 * Deleting only the entry therefore left a row behind that keeps being picked up as pending work
 * for a tracker the user deliberately unlinked, and that shadows the auto-create branch in
 * `syncManga` if the manga is ever linked again.
 */
class TrackRepositoryUnlinkTest {

    private val mangaId = 42L
    private val trackerId = TrackerType.ANILIST

    private val trackEntryDao: TrackEntryDao = mockk(relaxed = true)
    private val trackerSyncDao: TrackerSyncDao = mockk(relaxed = true)

    /** The one row the fake DAO holds, so a delete is observable by a later read. */
    private var stored: TrackerSyncStateEntity? = null

    private val staleRow = TrackerSyncStateEntity(
        id = 1L,
        mangaId = mangaId,
        trackerId = trackerId,
        // The remote id of the link being removed. A re-link to a different series must not
        // inherit it.
        remoteId = "53390",
        localLastChapterRead = 50f,
        localTotalChapters = 139,
        localStatus = MangaStatus.ONGOING.ordinal,
        localLastModified = Instant.parse("2026-01-01T00:00:00Z"),
        remoteLastChapterRead = 50f,
        remoteTotalChapters = 139,
        remoteStatus = MangaStatus.ONGOING.ordinal,
        remoteLastModified = null,
        syncStatus = 0,
        lastSyncAttempt = null,
        lastSuccessfulSync = Instant.parse("2026-01-01T00:00:00Z"),
        syncError = null,
    )

    private fun statefulDao() {
        stored = staleRow
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } answers { stored }
        coEvery { trackerSyncDao.deleteSyncState(mangaId, trackerId) } answers { stored = null }
        coEvery { trackerSyncDao.insertSyncState(any()) } answers {
            stored = firstArg<TrackerSyncStateEntity>().copy(id = 2L)
            2L
        }
    }

    @Test
    fun `unlinking a tracker deletes its sync-state row, not only the entry`() = runTest {
        statefulDao()
        val repository = TrackRepositoryImpl(trackEntryDao, trackerSyncDao)

        repository.deleteEntry(mangaId, trackerId)

        coVerify { trackEntryDao.deleteByMangaAndTracker(mangaId, trackerId) }
        assertNull("the sync-state row must not outlive the entry it tracks", stored)
    }

    @Test
    fun `unlinking scopes the delete to that tracker and leaves the others alone`() = runTest {
        statefulDao()
        val repository = TrackRepositoryImpl(trackEntryDao, trackerSyncDao)

        repository.deleteEntry(mangaId, trackerId)

        // deleteSyncStateForManga would take every tracker's row for this manga with it, which
        // would silently unlink MAL and Kitsu too.
        coVerify(exactly = 0) { trackerSyncDao.deleteSyncStateForManga(any()) }
        coVerify(exactly = 1) { trackerSyncDao.deleteSyncState(mangaId, trackerId) }
    }

    /**
     * The consequence the delete exists for.
     *
     * `syncManga` auto-creates a sync-state row only when it finds none. Leaving the old row in
     * place means a fresh link reuses it — pushing against the previous `remoteId`, and pinned to
     * the previous chapter high-water mark by the `maxOf` in `recordLocalChange`.
     */
    @Test
    fun `re-linking after an unlink starts from a fresh sync-state row`() = runTest {
        statefulDao()
        TrackRepositoryImpl(trackEntryDao, trackerSyncDao).deleteEntry(mangaId, trackerId)

        val newEntry = TrackEntry(
            remoteId = 99999L,
            mangaId = mangaId,
            trackerId = trackerId,
            lastChapterRead = 0f,
            totalChapters = 12,
        )
        val trackRepository: TrackRepository = mockk(relaxed = true)
        val trackManager: TrackManager = mockk(relaxed = true)
        val tracker: Tracker = mockk(relaxed = true)
        every { trackManager.get(trackerId) } returns tracker
        every { tracker.isLoggedIn } returns true
        coEvery { tracker.find(99999L) } returns newEntry
        coEvery { tracker.update(any()) } returns newEntry
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns newEntry
        every { trackRepository.observeEntriesForManga(mangaId) } returns flowOf(listOf(newEntry))
        coEvery { trackerSyncDao.getSyncConfiguration(trackerId) } returns null

        val inserted = slot<TrackerSyncStateEntity>()
        coEvery { trackerSyncDao.insertSyncState(capture(inserted)) } answers {
            stored = inserted.captured.copy(id = 2L)
            2L
        }

        TrackerSyncRepositoryImpl(trackerSyncDao, trackRepository, trackManager)
            .syncManga(mangaId, trackerId)

        // isCaptured, not captured != null: reading `captured` on an empty slot throws, so an
        // assertNotNull here would be asserting against an exception rather than a value.
        assertTrue(
            "syncManga should have auto-created a row; a surviving stale row would suppress it",
            inserted.isCaptured,
        )
        assertEquals(
            "the new row must carry the new link's remote id, not the unlinked one's",
            "99999",
            inserted.captured.remoteId,
        )
        assertEquals(
            "progress must start from the new entry, not the old link's high-water mark",
            0f,
            inserted.captured.localLastChapterRead,
            0.001f,
        )
    }
}
