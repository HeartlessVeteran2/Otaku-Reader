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
import kotlinx.coroutines.test.runTest
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
