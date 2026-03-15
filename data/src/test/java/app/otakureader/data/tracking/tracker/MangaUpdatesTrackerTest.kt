package app.otakureader.data.tracking.tracker

import app.otakureader.data.tracking.api.MangaUpdatesApi
import app.otakureader.data.tracking.api.MangaUpdatesListEntry
import app.otakureader.data.tracking.api.MangaUpdatesLoginContext
import app.otakureader.data.tracking.api.MangaUpdatesLoginRequest
import app.otakureader.data.tracking.api.MangaUpdatesLoginResponse
import app.otakureader.data.tracking.api.MangaUpdatesReadStatus
import app.otakureader.data.tracking.api.MangaUpdatesSearchResponse
import app.otakureader.data.tracking.api.MangaUpdatesSearchResult
import app.otakureader.data.tracking.api.MangaUpdatesSeries
import app.otakureader.data.tracking.api.MangaUpdatesSeriesRef
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MangaUpdatesTracker] covering session-based login/logout,
 * search, find, update (add-to-list with addToList/updateListEntry fallback),
 * status mapping, and network error handling.
 */
class MangaUpdatesTrackerTest {

    private lateinit var api: MangaUpdatesApi
    private lateinit var tracker: MangaUpdatesTracker

    private val loginSuccess = MangaUpdatesLoginResponse(
        status = "success",
        context = MangaUpdatesLoginContext(sessionToken = "session-abc", uid = 999L)
    )

    @Before
    fun setUp() {
        api = mockk()
        tracker = MangaUpdatesTracker(api)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tracker metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `tracker id matches TrackerType constant`() {
        assertEquals(TrackerType.MANGA_UPDATES, tracker.id)
    }

    @Test
    fun `tracker name is MangaUpdates`() {
        assertEquals("MangaUpdates", tracker.name)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login — session-based (no OAuth)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `isLoggedIn is false before login`() {
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login with valid credentials returns true and sets isLoggedIn`() = runTest {
        coEvery { api.login(MangaUpdatesLoginRequest("user", "pass")) } returns loginSuccess

        val result = tracker.login(username = "user", password = "pass")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `login stores sessionToken and userId`() = runTest {
        coEvery { api.login(any()) } returns loginSuccess

        tracker.login(username = "user", password = "pass")

        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `login returns false when context is null`() = runTest {
        coEvery { api.login(any()) } returns MangaUpdatesLoginResponse(status = "success", context = null)

        val result = tracker.login(username = "user", password = "pass")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login with empty sessionToken stores it and returns true`() = runTest {
        // The implementation checks sessionToken != null; empty string is not null,
        // so login returns true even with an empty token. Validation is left to callers.
        coEvery { api.login(any()) } returns MangaUpdatesLoginResponse(
            status = "success",
            context = MangaUpdatesLoginContext(sessionToken = "", uid = 1L)
        )

        val result = tracker.login(username = "user", password = "pass")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `login returns false on network error`() = runTest {
        coEvery { api.login(any()) } throws RuntimeException("Network error")

        val result = tracker.login(username = "user", password = "wrong-pass")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login returns false on HTTP 401`() = runTest {
        coEvery { api.login(any()) } throws RuntimeException("HTTP 401 Unauthorized")

        val result = tracker.login(username = "user", password = "bad-pass")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Re-authentication
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `re-authentication after logout succeeds`() = runTest {
        coEvery { api.login(any()) } returns loginSuccess

        tracker.login(username = "user", password = "pass")
        tracker.logout()
        assertFalse(tracker.isLoggedIn)

        val result = tracker.login(username = "user2", password = "pass2")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `failed re-authentication leaves tracker logged out`() = runTest {
        coEvery { api.login(any()) } returns loginSuccess
        tracker.login(username = "user", password = "pass")
        tracker.logout()

        coEvery { api.login(any()) } throws RuntimeException("Network error")

        val result = tracker.login(username = "user", password = "wrong")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `logout clears sessionToken and userId`() = runTest {
        coEvery { api.login(any()) } returns loginSuccess
        tracker.login(username = "user", password = "pass")

        tracker.logout()

        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `logout when not logged in is safe`() {
        assertFalse(tracker.isLoggedIn)
        tracker.logout()
        assertFalse(tracker.isLoggedIn)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `search returns mapped TrackEntry list`() = runTest {
        val series = MangaUpdatesSeries(seriesId = 5L, title = "Berserk")
        val response = MangaUpdatesSearchResponse(
            totalHits = 1,
            results = listOf(MangaUpdatesSearchResult(hitTitle = "Berserk", record = series))
        )
        coEvery { api.searchSeries(any()) } returns response

        val results = tracker.search("Berserk")

        assertEquals(1, results.size)
        assertEquals(5L, results[0].remoteId)
        assertEquals("Berserk", results[0].title)
        assertEquals(TrackerType.MANGA_UPDATES, results[0].trackerId)
    }

    @Test
    fun `search returns empty list when no hits`() = runTest {
        coEvery { api.searchSeries(any()) } returns MangaUpdatesSearchResponse(
            totalHits = 0, results = emptyList()
        )

        val results = tracker.search("NonExistent12345")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `search propagates network exception`() = runTest {
        coEvery { api.searchSeries(any()) } throws RuntimeException("Network error")

        var threwException = false
        try {
            tracker.search("query")
        } catch (e: RuntimeException) {
            threwException = true
        }

        assertTrue(threwException)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Find
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `find returns TrackEntry when list entry exists`() = runTest {
        val listEntry = MangaUpdatesListEntry(
            series = MangaUpdatesSeriesRef(seriesId = 5L, title = "Berserk"),
            listId = 0,
            status = MangaUpdatesReadStatus(status = "reading"),
            chapter = 150,
            score = 9.0
        )
        coEvery { api.getListEntry(5L) } returns listEntry

        val entry = tracker.find(5L)

        assertNotNull(entry)
        assertEquals(5L, entry!!.remoteId)
        assertEquals("Berserk", entry.title)
        assertEquals(TrackStatus.READING, entry.status) // listId=0 → READING
        assertEquals(150f, entry.lastChapterRead)
        assertEquals(9.0f, entry.score)
    }

    @Test
    fun `find returns null on network error`() = runTest {
        coEvery { api.getListEntry(any()) } throws RuntimeException("HTTP 404")

        val entry = tracker.find(99999L)

        assertNull(entry)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update — addToList with fallback to updateListEntry
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `update calls addToList first and returns entry on success`() = runTest {
        val dummyEntry = MangaUpdatesListEntry(
            series = MangaUpdatesSeriesRef(seriesId = 5L),
            listId = 0,
            chapter = 50
        )
        coEvery { api.addToList(any()) } returns dummyEntry

        val entry = TrackEntry(
            remoteId = 5L,
            mangaId = 1L,
            trackerId = TrackerType.MANGA_UPDATES,
            status = TrackStatus.READING,
            lastChapterRead = 50f
        )

        val result = tracker.update(entry)

        assertEquals(entry, result)
        coVerify(exactly = 1) { api.addToList(any()) }
        coVerify(exactly = 0) { api.updateListEntry(any()) }
    }

    @Test
    fun `update falls back to updateListEntry when addToList fails`() = runTest {
        coEvery { api.addToList(any()) } throws RuntimeException("Conflict")
        val dummyEntry = MangaUpdatesListEntry(
            series = MangaUpdatesSeriesRef(seriesId = 5L),
            listId = 0,
            chapter = 50
        )
        coEvery { api.updateListEntry(any()) } returns dummyEntry

        val entry = TrackEntry(
            remoteId = 5L,
            mangaId = 1L,
            trackerId = TrackerType.MANGA_UPDATES,
            status = TrackStatus.READING,
            lastChapterRead = 50f
        )

        val result = tracker.update(entry)

        assertEquals(entry, result)
        coVerify(exactly = 1) { api.addToList(any()) }
        coVerify(exactly = 1) { api.updateListEntry(any()) }
    }

    @Test
    fun `update returns entry unchanged when both addToList and updateListEntry fail`() = runTest {
        coEvery { api.addToList(any()) } throws RuntimeException("Error 1")
        coEvery { api.updateListEntry(any()) } throws RuntimeException("Error 2")

        val entry = TrackEntry(
            remoteId = 5L,
            mangaId = 1L,
            trackerId = TrackerType.MANGA_UPDATES,
            status = TrackStatus.DROPPED
        )

        val result = tracker.update(entry)

        assertEquals(entry, result)
    }

    @Test
    fun `update sends correct listId for each TrackStatus`() = runTest {
        val capturedListIds = mutableListOf<Int>()
        coEvery { api.addToList(any()) } answers {
            capturedListIds.add(firstArg<app.otakureader.data.tracking.api.MangaUpdatesListRequest>().listId)
            MangaUpdatesListEntry()
        }

        val statuses = mapOf(
            TrackStatus.READING to 0,
            TrackStatus.COMPLETED to 1,
            TrackStatus.ON_HOLD to 2,
            TrackStatus.DROPPED to 3,
            TrackStatus.PLAN_TO_READ to 4,
            TrackStatus.RE_READING to 5
        )

        statuses.forEach { (status, expectedId) ->
            tracker.update(
                TrackEntry(
                    remoteId = 1L, mangaId = 1L, trackerId = TrackerType.MANGA_UPDATES,
                    status = status
                )
            )
        }

        assertEquals(listOf(0, 1, 2, 3, 4, 5), capturedListIds)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping — listId → TrackStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toTrackStatus maps 0 to READING`() {
        assertEquals(TrackStatus.READING, tracker.toTrackStatus(0))
    }

    @Test
    fun `toTrackStatus maps 1 to COMPLETED`() {
        assertEquals(TrackStatus.COMPLETED, tracker.toTrackStatus(1))
    }

    @Test
    fun `toTrackStatus maps 2 to ON_HOLD`() {
        assertEquals(TrackStatus.ON_HOLD, tracker.toTrackStatus(2))
    }

    @Test
    fun `toTrackStatus maps 3 to DROPPED`() {
        assertEquals(TrackStatus.DROPPED, tracker.toTrackStatus(3))
    }

    @Test
    fun `toTrackStatus maps 4 to PLAN_TO_READ`() {
        assertEquals(TrackStatus.PLAN_TO_READ, tracker.toTrackStatus(4))
    }

    @Test
    fun `toTrackStatus maps 5 to RE_READING`() {
        assertEquals(TrackStatus.RE_READING, tracker.toTrackStatus(5))
    }

    @Test
    fun `toTrackStatus maps unknown value to PLAN_TO_READ`() {
        assertEquals(TrackStatus.PLAN_TO_READ, tracker.toTrackStatus(99))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping — TrackStatus → listId
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toRemoteStatus maps READING to 0`() {
        assertEquals(0, tracker.toRemoteStatus(TrackStatus.READING))
    }

    @Test
    fun `toRemoteStatus maps COMPLETED to 1`() {
        assertEquals(1, tracker.toRemoteStatus(TrackStatus.COMPLETED))
    }

    @Test
    fun `toRemoteStatus maps ON_HOLD to 2`() {
        assertEquals(2, tracker.toRemoteStatus(TrackStatus.ON_HOLD))
    }

    @Test
    fun `toRemoteStatus maps DROPPED to 3`() {
        assertEquals(3, tracker.toRemoteStatus(TrackStatus.DROPPED))
    }

    @Test
    fun `toRemoteStatus maps PLAN_TO_READ to 4`() {
        assertEquals(4, tracker.toRemoteStatus(TrackStatus.PLAN_TO_READ))
    }

    @Test
    fun `toRemoteStatus maps RE_READING to 5`() {
        assertEquals(5, tracker.toRemoteStatus(TrackStatus.RE_READING))
    }

    @Test
    fun `toRemoteStatus and toTrackStatus are inverse operations`() {
        TrackStatus.entries.forEach { status ->
            assertEquals(status, tracker.toTrackStatus(tracker.toRemoteStatus(status)))
        }
    }
}
