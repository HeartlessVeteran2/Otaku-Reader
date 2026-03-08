package app.otakureader.data.tracker

import app.otakureader.domain.model.TrackItem
import app.otakureader.domain.model.TrackService
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.repository.TrackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackManagerTest {

    private lateinit var malTracker: BaseTracker
    private lateinit var aniListTracker: BaseTracker
    private lateinit var trackRepository: TrackRepository
    private lateinit var trackManager: TrackManager

    private fun makeTrackItem(
        mangaId: Long = 1L,
        service: TrackService = TrackService.MAL,
        remoteId: Long = 100L,
        lastChapterRead: Float = 5f
    ) = TrackItem(
        id = 1L,
        mangaId = mangaId,
        service = service,
        remoteId = remoteId,
        title = "Test Manga",
        lastChapterRead = lastChapterRead
    )

    @Before
    fun setUp() {
        malTracker = mockk()
        aniListTracker = mockk()
        trackRepository = mockk()

        coEvery { malTracker.service } returns TrackService.MAL
        coEvery { aniListTracker.service } returns TrackService.ANILIST

        trackManager = TrackManager(
            trackers = setOf(malTracker, aniListTracker),
            trackRepository = trackRepository
        )
    }

    // ---- getTracker ----

    @Test
    fun getTracker_knownService_returnsTracker() {
        val tracker = trackManager.getTracker(TrackService.MAL)
        assertEquals(malTracker, tracker)
    }

    @Test
    fun getTracker_unknownService_returnsNull() {
        val tracker = trackManager.getTracker(TrackService.KITSU)
        assertNull(tracker)
    }

    // ---- isLoggedIn ----

    @Test
    fun isLoggedIn_whenLoggedIn_returnsTrue() = runTest {
        coEvery { malTracker.isLoggedIn() } returns true
        assertTrue(trackManager.isLoggedIn(TrackService.MAL))
    }

    @Test
    fun isLoggedIn_whenNotLoggedIn_returnsFalse() = runTest {
        coEvery { malTracker.isLoggedIn() } returns false
        assertFalse(trackManager.isLoggedIn(TrackService.MAL))
    }

    @Test
    fun isLoggedIn_unknownService_returnsFalse() = runTest {
        assertFalse(trackManager.isLoggedIn(TrackService.KITSU))
    }

    // ---- updateTracking ----

    @Test
    fun updateTracking_loggedIn_updatesAndPersists() = runTest {
        val track = makeTrackItem()
        coEvery { malTracker.isLoggedIn() } returns true
        coEvery { malTracker.update(track) } returns Unit
        coEvery { trackRepository.upsertTrack(track) } returns Unit

        trackManager.updateTracking(track)

        coVerify { malTracker.update(track) }
        coVerify { trackRepository.upsertTrack(track) }
    }

    @Test
    fun updateTracking_notLoggedIn_noOp() = runTest {
        val track = makeTrackItem()
        coEvery { malTracker.isLoggedIn() } returns false

        trackManager.updateTracking(track)

        coVerify(exactly = 0) { malTracker.update(any()) }
        coVerify(exactly = 0) { trackRepository.upsertTrack(any()) }
    }

    // ---- onChapterRead ----

    @Test
    fun onChapterRead_greaterThanLastRead_updatesTrack() = runTest {
        val mangaId = 1L
        val existing = makeTrackItem(mangaId = mangaId, lastChapterRead = 3f)

        coEvery { trackRepository.getTracksSnapshot(mangaId) } returns listOf(existing)
        coEvery { malTracker.isLoggedIn() } returns true
        coEvery { malTracker.update(any()) } returns Unit
        coEvery { trackRepository.upsertTrack(any()) } returns Unit

        trackManager.onChapterRead(mangaId, chapterNumber = 4f)

        coVerify { malTracker.update(match { it.lastChapterRead == 4f }) }
    }

    @Test
    fun onChapterRead_lessThanOrEqualLastRead_doesNotUpdate() = runTest {
        val mangaId = 1L
        val existing = makeTrackItem(mangaId = mangaId, lastChapterRead = 5f)

        coEvery { trackRepository.getTracksSnapshot(mangaId) } returns listOf(existing)

        trackManager.onChapterRead(mangaId, chapterNumber = 5f)

        coVerify(exactly = 0) { malTracker.update(any()) }
    }

    // ---- searchManga ----

    @Test
    fun searchManga_registeredTracker_returnsResults() = runTest {
        val results = listOf(makeTrackItem().copy(id = 0))
        coEvery { malTracker.searchManga("Naruto") } returns results

        val actual = trackManager.searchManga(TrackService.MAL, "Naruto")

        assertEquals(results, actual)
    }

    @Test
    fun searchManga_unknownService_returnsEmptyList() = runTest {
        val results = trackManager.searchManga(TrackService.KITSU, "Naruto")
        assertTrue(results.isEmpty())
    }
}
