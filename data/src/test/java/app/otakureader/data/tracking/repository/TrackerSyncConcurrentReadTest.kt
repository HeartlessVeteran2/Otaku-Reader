package app.otakureader.data.tracking.repository

import app.otakureader.core.database.dao.TrackerSyncDao
import app.otakureader.core.database.entity.TrackerSyncStateEntity
import app.otakureader.data.tracking.TrackManager
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.SyncStatus
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A chapter finished *while a sync is in flight* must not be swallowed by that sync.
 *
 * `syncManga` reads the sync-state row, makes a network call, then writes `snapshot.copy(...)`.
 * The copy carries what the row held before the request, so a read landing in between was
 * overwritten and the row marked SYNCED — and because `localChanged` is
 * `localLastModified > lastSuccessfulSync`, advancing the sync stamp meant the next sync saw
 * nothing to send either. The chapter was lost with no error anywhere.
 */
class TrackerSyncConcurrentReadTest {

    private val mangaId = 42L
    private val trackerId = TrackerType.ANILIST
    private val baseTime: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private val trackerSyncDao: TrackerSyncDao = mockk(relaxed = true)
    private val trackRepository: TrackRepository = mockk(relaxed = true)
    private val trackManager: TrackManager = mockk(relaxed = true)
    private val tracker: Tracker = mockk(relaxed = true)
    private val repository = TrackerSyncRepositoryImpl(trackerSyncDao, trackRepository, trackManager)

    private fun entry(lastChapterRead: Float) = TrackEntry(
        remoteId = 53390L,
        mangaId = mangaId,
        trackerId = trackerId,
        lastChapterRead = lastChapterRead,
        totalChapters = 139,
    )

    private fun syncState(
        localLastChapterRead: Float,
        localLastModified: Instant,
    ) = TrackerSyncStateEntity(
        id = 1L,
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = "53390",
        localLastChapterRead = localLastChapterRead,
        localTotalChapters = 139,
        localStatus = MangaStatus.ONGOING.ordinal,
        localLastModified = localLastModified,
        remoteLastChapterRead = 50f,
        remoteTotalChapters = 139,
        remoteStatus = MangaStatus.ONGOING.ordinal,
        remoteLastModified = null,
        syncStatus = 0,
        lastSyncAttempt = null,
        // Older than localLastModified, so localChanged is true and the push path is taken.
        lastSuccessfulSync = baseTime,
        syncError = null,
    )

    /** The snapshot syncManga starts from, then the row as it stands after a mid-flight read. */
    private fun stubReadDuringSync() {
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returnsMany listOf(
            syncState(localLastChapterRead = 50f, localLastModified = baseTime.plusSeconds(10)),
            syncState(localLastChapterRead = 51f, localLastModified = baseTime.plusSeconds(20)),
        )
    }

    private fun stubLoggedInTracker() {
        every { trackManager.get(trackerId) } returns tracker
        every { tracker.isLoggedIn } returns true
        coEvery { tracker.find(53390L) } returns entry(lastChapterRead = 50f)
        coEvery { tracker.update(any()) } returns entry(lastChapterRead = 50f)
        coEvery { trackerSyncDao.getSyncConfiguration(trackerId) } returns null
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns entry(lastChapterRead = 50f)
    }

    @Test
    fun `a chapter read during a push keeps the row pending instead of marking it synced`() = runTest {
        stubLoggedInTracker()
        stubReadDuringSync()

        repository.syncManga(mangaId, trackerId)

        val saved = slot<TrackerSyncStateEntity>()
        coVerify { trackerSyncDao.updateSyncState(capture(saved)) }
        assertEquals(
            "a read that arrived mid-push has not been sent, so the row owes another sync",
            SyncStatus.PENDING.ordinal,
            saved.captured.syncStatus,
        )
    }

    @Test
    fun `a chapter read during a push survives rather than being overwritten by the snapshot`() = runTest {
        stubLoggedInTracker()
        stubReadDuringSync()

        repository.syncManga(mangaId, trackerId)

        val saved = slot<TrackerSyncStateEntity>()
        coVerify { trackerSyncDao.updateSyncState(capture(saved)) }
        assertEquals(51f, saved.captured.localLastChapterRead, 0f)
    }

    @Test
    fun `the sync stamp is not advanced past a read that has not been sent`() = runTest {
        stubLoggedInTracker()
        stubReadDuringSync()

        repository.syncManga(mangaId, trackerId)

        // localChanged is `localLastModified > lastSuccessfulSync`. Stamping the sync as
        // successful *now* would put it after the unsent read, and the next sync would conclude
        // there was nothing to send — losing the chapter permanently and silently.
        val saved = slot<TrackerSyncStateEntity>()
        coVerify { trackerSyncDao.updateSyncState(capture(saved)) }
        assertEquals(baseTime, saved.captured.lastSuccessfulSync)
    }

    @Test
    fun `an undisturbed push still marks the row synced`() = runTest {
        stubLoggedInTracker()
        // The same row both times: nothing landed while the push was in flight.
        val steady = syncState(localLastChapterRead = 51f, localLastModified = baseTime.plusSeconds(10))
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returns steady

        repository.syncManga(mangaId, trackerId)

        val saved = slot<TrackerSyncStateEntity>()
        coVerify { trackerSyncDao.updateSyncState(capture(saved)) }
        assertEquals(SyncStatus.SYNCED.ordinal, saved.captured.syncStatus)
    }

    /**
     * The remote-wins branch must leave the *pre-fetch* remote snapshot in place.
     *
     * `remoteChanged` is `remoteEntry.lastChapterRead != syncState.remoteLastChapterRead`.
     * Recording the freshly-fetched value when bailing out would make the next sync compare it
     * against itself, find them equal, and conclude the remote had not moved — leaving only
     * `localChanged` true, which is the clean local-push path. The remote chapter this branch
     * exists to preserve would be overwritten with no conflict prompt and no error.
     *
     * So the assertion is on the *next* sync's verdict, not on what was written: it has to still
     * see a conflict.
     */
    @Test
    fun `a read during a remote-wins fetch leaves the next sync able to see the conflict`() = runTest {
        every { trackManager.get(trackerId) } returns tracker
        every { tracker.isLoggedIn } returns true
        coEvery { trackerSyncDao.getSyncConfiguration(trackerId) } returns null
        // Remote moved to 60 while the stored snapshot still says 50, so remoteChanged is true.
        coEvery { tracker.find(53390L) } returns entry(lastChapterRead = 60f)
        coEvery { tracker.update(any()) } returns entry(lastChapterRead = 60f)
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns entry(lastChapterRead = 51f)

        // Stateful, deliberately: a `returnsMany` stub would hand pass 2 a canned row no matter
        // what pass 1 wrote, so the write this test is about would be invisible and the test
        // would pass with the bug reinstated.
        var stored = syncState(localLastChapterRead = 50f, localLastModified = baseTime)
        var reads = 0
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } answers {
            reads++
            // Read 2 is localChangeSince, mid-fetch. Stand in for a recordLocalChange landing
            // right there.
            if (reads == 2) {
                stored = stored.copy(
                    localLastChapterRead = 51f,
                    localLastModified = baseTime.plusSeconds(20),
                )
            }
            stored
        }
        coEvery { trackerSyncDao.updateSyncState(any()) } answers { stored = firstArg() }

        repository.syncManga(mangaId, trackerId)
        val second = repository.syncManga(mangaId, trackerId)

        assertEquals(
            "both sides moved, so the second pass owes the user a conflict rather than a push",
            true,
            second.hasConflict,
        )
    }

    /**
     * Two reads finishing at once must not lose the higher one.
     *
     * `recordLocalChange` is a read-modify-write: it reads the row, takes
     * `maxOf(existing.localLastChapterRead, chapterRead)`, and writes it back. Interleaved, both
     * calls read the same pre-state, and whichever writes *last* wins outright — so a lower
     * chapter number landing second erases the higher one. The row lock is what makes the pair
     * atomic.
     *
     * The DAO fake snapshots the row and *then* yields. Yielding first and reading afterwards
     * looks equivalent and is not: the late read observes whatever the other coroutine already
     * wrote, so both calls see consistent state and the test passes with the lock deleted. The
     * read has to happen at the moment the caller's read happens.
     */
    @Test
    fun `two concurrent reads do not lose the higher chapter`() = runTest {
        var stored = syncState(localLastChapterRead = 50f, localLastModified = baseTime)
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } coAnswers {
            val snapshot = stored
            yield()
            snapshot
        }
        coEvery { trackerSyncDao.updateSyncState(any()) } answers {
            stored = firstArg()
        }
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns null

        // Higher first: without the lock the second call reads the stale 50, takes max(50, 51),
        // and writes 51 over the 52 that had already landed.
        val first = launch { repository.recordLocalChange(mangaId, trackerId, 52f, MangaStatus.ONGOING) }
        val second = launch { repository.recordLocalChange(mangaId, trackerId, 51f, MangaStatus.ONGOING) }
        first.join()
        second.join()

        assertEquals(52f, stored.localLastChapterRead, 0f)
    }

    /**
     * Unlinking mid-fetch must not bring the link back.
     *
     * `applyRemoteToLocal` upserts `localEntry ?: remoteEntry` — a fallback meant for "tracked on
     * the service, no local row yet". After an unlink, `getEntry` also returns null, so the
     * fallback fired and recreated the entry from the fetched remote value: the manga reappeared
     * as tracked moments after the user removed it. A deleted row is a third state, not an
     * unchanged one.
     */
    @Test
    fun `unlinking during a remote fetch does not resurrect the entry`() = runTest {
        every { trackManager.get(trackerId) } returns tracker
        every { tracker.isLoggedIn } returns true
        coEvery { trackerSyncDao.getSyncConfiguration(trackerId) } returns null
        coEvery { tracker.find(53390L) } returns entry(lastChapterRead = 60f)
        // No local entry, exactly as after deleteEntry — this is what armed the fallback.
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns null

        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returnsMany listOf(
            // Routes to the remote-wins branch: localChanged false, remoteChanged true.
            syncState(localLastChapterRead = 50f, localLastModified = baseTime),
            // The unlink landed during tracker.find, taking the sync-state row with it.
            null,
        )

        val result = repository.syncManga(mangaId, trackerId)

        coVerify(exactly = 0) { trackRepository.upsertEntry(any()) }
        coVerify(exactly = 0) { trackerSyncDao.updateSyncState(any()) }
        assertEquals("an unlinked tracker did not sync, so do not claim it did", false, result.success)
    }

    /**
     * The no-op path reaches the same lost update by a different route.
     *
     * When neither side changed as of the snapshot, `syncManga` calls `markSyncSuccess`, which
     * advances `lastSuccessfulSync` and sets SYNCED. `localChanged` is
     * `localLastModified > lastSuccessfulSync`, so doing that to a row a chapter read has just
     * marked PENDING drops the read: the next sync sees nothing to send.
     */
    @Test
    fun `a read during a no-op sync is not flipped back to synced`() = runTest {
        every { trackManager.get(trackerId) } returns tracker
        every { tracker.isLoggedIn } returns true
        coEvery { trackerSyncDao.getSyncConfiguration(trackerId) } returns null
        // Remote matches the stored snapshot, so remoteChanged is false; localLastModified equals
        // lastSuccessfulSync, so localChanged is false. Nothing to sync.
        coEvery { tracker.find(53390L) } returns entry(lastChapterRead = 50f)
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns entry(lastChapterRead = 50f)

        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returnsMany listOf(
            syncState(localLastChapterRead = 50f, localLastModified = baseTime),
            // A chapter finished while the remote was being fetched.
            syncState(localLastChapterRead = 51f, localLastModified = baseTime.plusSeconds(20)),
        )

        repository.syncManga(mangaId, trackerId)

        coVerify(exactly = 0) { trackerSyncDao.markSyncSuccess(any(), any(), any()) }
    }

    /**
     * A relink during a push must not inherit the old link's data.
     *
     * `getSyncState` looks up by (mangaId, trackerId) — a key that is *reused*. Unlink then relink
     * deletes the row and inserts a fresh one under the same key, so the lookup succeeds and the
     * new row's timestamp is naturally newer. Without an identity check that reads as "a local
     * change landed", and the push stamps the new link's row with the old link's tracker response.
     */
    @Test
    fun `a relink during a push is not mistaken for a local change`() = runTest {
        stubLoggedInTracker()
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returnsMany listOf(
            syncState(localLastChapterRead = 50f, localLastModified = baseTime.plusSeconds(10)),
            // Same manga, same tracker, different row: id 2 is the relinked series.
            syncState(localLastChapterRead = 0f, localLastModified = baseTime.plusSeconds(20))
                .copy(id = 2L, remoteId = "99999"),
        )

        val result = repository.syncManga(mangaId, trackerId)

        coVerify(exactly = 0) { trackerSyncDao.updateSyncState(any()) }
        assertEquals(false, result.success)
    }

    /**
     * A failure *returned* rather than thrown still has to clear SYNCING.
     *
     * Every entry point stamps SYNCING before its request. `getPendingSyncs` selects
     * `syncStatus = PENDING`, so a row left at SYNCING is never picked up by `syncAllPending`
     * again — the manga just stops syncing, with a spinner as the only symptom.
     */
    @Test
    fun `a returned failure does not leave the row stuck in syncing`() = runTest {
        stubLoggedInTracker()
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns null
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returns
            syncState(localLastChapterRead = 51f, localLastModified = baseTime.plusSeconds(10))

        repository.syncManga(mangaId, trackerId)

        val status = slot<Int>()
        coVerify { trackerSyncDao.markSyncError(eq(1L), capture(status), any(), any()) }
        assertEquals(
            "SYNCING is terminal for syncAllPending, which only selects PENDING",
            SyncStatus.ERROR.ordinal,
            status.captured,
        )
    }

    @Test
    fun `pushing with no local entry reports failure rather than silent success`() = runTest {
        stubLoggedInTracker()
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns null
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returns
            syncState(localLastChapterRead = 51f, localLastModified = baseTime.plusSeconds(10))

        val result = repository.syncManga(mangaId, trackerId)

        // It used to return SyncResult(true, "Sync successful") having pushed nothing at all.
        assertEquals(false, result.success)
    }
}
