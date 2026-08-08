package app.otakureader.data.tracking.repository

import app.otakureader.core.database.dao.TrackerSyncDao
import app.otakureader.core.database.entity.TrackerSyncStateEntity
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackerType
import app.otakureader.data.tracking.TrackManager
import app.otakureader.domain.tracking.TrackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the hand-off between reading a chapter and pushing that progress to a tracker.
 *
 * The two sides were not connected: `recordLocalChange` wrote `localLastChapterRead` on the
 * sync-state row, and `syncManga`'s local-wins branch builds its payload from
 * `trackRepository.getEntry(...)`. Nothing carried the number across, so finishing a chapter
 * pushed the previous entry unchanged and then marked the sync SYNCED.
 */
class TrackerSyncRecordLocalChangeTest {

    private val mangaId = 42L
    private val trackerId = TrackerType.ANILIST

    private val trackerSyncDao: TrackerSyncDao = mockk(relaxed = true)
    private val trackRepository: TrackRepository = mockk(relaxed = true)
    private val trackManager: TrackManager = mockk(relaxed = true)
    private val repository = TrackerSyncRepositoryImpl(trackerSyncDao, trackRepository, trackManager)

    private fun entry(lastChapterRead: Float) = TrackEntry(
        remoteId = 53390L,
        mangaId = mangaId,
        trackerId = trackerId,
        lastChapterRead = lastChapterRead,
        totalChapters = 139,
    )

    private fun syncState(localLastChapterRead: Float) = TrackerSyncStateEntity(
        id = 1L,
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = "53390",
        localLastChapterRead = localLastChapterRead,
        localTotalChapters = 139,
        localStatus = MangaStatus.ONGOING.ordinal,
        localLastModified = Instant.EPOCH,
        remoteLastChapterRead = localLastChapterRead,
        remoteTotalChapters = 139,
        remoteStatus = MangaStatus.ONGOING.ordinal,
        remoteLastModified = null,
        syncStatus = 0,
        lastSyncAttempt = null,
        lastSuccessfulSync = null,
        syncError = null,
    )

    @Test
    fun `reading a new chapter advances the entry the push actually sends`() = runTest {
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns entry(lastChapterRead = 50f)
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returns syncState(50f)

        repository.recordLocalChange(mangaId, trackerId, chapterRead = 51f, status = MangaStatus.ONGOING)

        // The TrackEntry is what syncManga pushes. Recording only into the sync-state row left
        // this untouched, so reading never moved progress on the tracker at all.
        val saved = slot<TrackEntry>()
        coVerify(exactly = 1) { trackRepository.upsertEntry(capture(saved)) }
        assertEquals(51f, saved.captured.lastChapterRead, 0f)
    }

    @Test
    fun `re-reading an old chapter never moves progress backwards`() = runTest {
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns entry(lastChapterRead = 50f)
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returns syncState(50f)

        repository.recordLocalChange(mangaId, trackerId, chapterRead = 5f, status = MangaStatus.ONGOING)

        // Re-reading is normal and must not tell AniList the user un-read forty-five chapters.
        coVerify(exactly = 0) { trackRepository.upsertEntry(any()) }
    }

    @Test
    fun `re-reading cannot manufacture a conflict on the sync state either`() = runTest {
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns entry(lastChapterRead = 50f)
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returns syncState(50f)

        repository.recordLocalChange(mangaId, trackerId, chapterRead = 5f, status = MangaStatus.ONGOING)

        // localLastChapterRead feeds the conflict check against the remote value. Writing 5 here
        // would disagree with an unchanged remote 50 and hand the user a resolution prompt for a
        // conflict they created by opening chapter 5.
        val saved = slot<TrackerSyncStateEntity>()
        coVerify(exactly = 1) { trackerSyncDao.updateSyncState(capture(saved)) }
        assertEquals(50f, saved.captured.localLastChapterRead, 0f)
    }

    @Test
    fun `a status change is still recorded when no chapter was read`() = runTest {
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns entry(lastChapterRead = 50f)
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returns syncState(50f)

        repository.recordLocalChange(mangaId, trackerId, chapterRead = 5f, status = MangaStatus.CANCELLED)

        // Dropping a manga is a real change even with no progress, which is why the status write
        // and the PENDING marking sit outside the progress guard.
        val saved = slot<TrackerSyncStateEntity>()
        coVerify(exactly = 1) { trackerSyncDao.updateSyncState(capture(saved)) }
        assertEquals(MangaStatus.CANCELLED.ordinal, saved.captured.localStatus)
    }

    @Test
    fun `a manga with no local entry yet records nothing rather than inventing one`() = runTest {
        coEvery { trackRepository.getEntry(mangaId, trackerId) } returns null
        coEvery { trackerSyncDao.getSyncState(mangaId, trackerId) } returns null

        repository.recordLocalChange(mangaId, trackerId, chapterRead = 3f, status = MangaStatus.ONGOING)

        coVerify(exactly = 0) { trackRepository.upsertEntry(any()) }
        coVerify(exactly = 0) { trackerSyncDao.updateSyncState(any()) }
    }
}
