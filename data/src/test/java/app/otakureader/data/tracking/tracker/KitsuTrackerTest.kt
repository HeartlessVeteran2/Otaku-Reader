package app.otakureader.data.tracking.tracker

import app.otakureader.data.tracking.api.KitsuApi
import app.otakureader.data.tracking.api.KitsuAttributes
import app.otakureader.data.tracking.api.KitsuLibraryEntryResponse
import app.otakureader.data.tracking.api.KitsuOAuthApi
import app.otakureader.data.tracking.api.KitsuPagedResponse
import app.otakureader.data.tracking.api.KitsuResource
import app.otakureader.data.tracking.api.KitsuTokenResponse
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
 * Unit tests for [KitsuTracker] covering OAuth password-grant login/logout,
 * re-authentication, user-ID resolution, search, find, update, status mapping,
 * and network error handling.
 */
class KitsuTrackerTest {

    private lateinit var oauthApi: KitsuOAuthApi
    private lateinit var api: KitsuApi
    private lateinit var tracker: KitsuTracker

    private val clientId = "kitsu-client-id"
    private val clientSecret = "kitsu-client-secret"

    private val tokenResponse = KitsuTokenResponse(
        accessToken = "kitsu-access-token",
        refreshToken = "kitsu-refresh-token",
        expiresIn = 3600L,
        tokenType = "Bearer"
    )

    private fun userResponse(id: String = "77") = KitsuPagedResponse(
        data = listOf(KitsuResource(id = id, type = "users", attributes = KitsuAttributes()))
    )

    @Before
    fun setUp() {
        oauthApi = mockk()
        api = mockk()
        tracker = KitsuTracker(oauthApi, api, clientId, clientSecret)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tracker metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `tracker id matches TrackerType constant`() {
        assertEquals(TrackerType.KITSU, tracker.id)
    }

    @Test
    fun `tracker name is Kitsu`() {
        assertEquals("Kitsu", tracker.name)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login — OAuth password-grant flow
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `isLoggedIn is false before login`() {
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login with valid credentials returns true and sets isLoggedIn`() = runTest {
        coEvery {
            oauthApi.getAccessToken(
                username = "user@example.com",
                password = "secret",
                clientId = clientId,
                clientSecret = clientSecret
            )
        } returns tokenResponse
        coEvery { api.getCurrentUser() } returns userResponse()

        val result = tracker.login(username = "user@example.com", password = "secret")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `login resolves and stores userId on success`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } returns userResponse(id = "123")

        tracker.login(username = "user@example.com", password = "pass")

        // userId is used internally; confirm tracker is logged in
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `login returns false and clears tokens when user fetch fails`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } returns KitsuPagedResponse(data = emptyList())

        val result = tracker.login(username = "user@example.com", password = "pass")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login returns false on OAuth network error`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } throws RuntimeException("Network error")

        val result = tracker.login(username = "user@example.com", password = "wrong-pass")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login returns false when getCurrentUser throws exception`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } throws RuntimeException("Server error")

        val result = tracker.login(username = "user@example.com", password = "pass")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Re-authentication
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `re-authentication after logout succeeds`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } returns userResponse()

        tracker.login(username = "user@example.com", password = "pass")
        tracker.logout()
        assertFalse(tracker.isLoggedIn)

        val result = tracker.login(username = "user2@example.com", password = "pass2")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `failed re-authentication leaves tracker logged out`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } returns userResponse()
        tracker.login(username = "user@example.com", password = "pass")
        tracker.logout()

        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } throws RuntimeException("401 Unauthorized")

        val result = tracker.login(username = "user@example.com", password = "wrong")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `provider migration — find after re-login uses new userId`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } returns userResponse(id = "100")
        tracker.login(username = "old@example.com", password = "pass")

        tracker.logout()

        coEvery { api.getCurrentUser() } returns userResponse(id = "200")
        tracker.login(username = "new@example.com", password = "pass")

        // After re-login with userId=200, find() should call the API with the new userId
        val attrs = KitsuAttributes(status = "current", progressedChapters = 5, ratingTwenty = null)
        val libraryEntry = KitsuResource(id = "1", type = "libraryEntries", attributes = attrs)
        coEvery { api.findLibraryEntry(mangaId = 42L, userId = 200L) } returns KitsuPagedResponse(
            data = listOf(libraryEntry)
        )

        val entry = tracker.find(42L)

        assertNotNull(entry)
        coVerify(exactly = 1) { api.findLibraryEntry(mangaId = 42L, userId = 200L) }
        coVerify(exactly = 0) { api.findLibraryEntry(mangaId = 42L, userId = 100L) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `logout clears tokens and userId`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } returns userResponse()
        tracker.login(username = "user@example.com", password = "pass")

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
        val resource = KitsuResource(
            id = "5",
            type = "manga",
            attributes = KitsuAttributes(
                canonicalTitle = "One Piece",
                chapterCount = 1000
            )
        )
        coEvery { api.searchManga(query = "One Piece") } returns KitsuPagedResponse(data = listOf(resource))

        val results = tracker.search("One Piece")

        assertEquals(1, results.size)
        assertEquals(5L, results[0].remoteId)
        assertEquals("One Piece", results[0].title)
        assertEquals(1000, results[0].totalChapters)
        assertEquals(TrackerType.KITSU, results[0].trackerId)
    }

    @Test
    fun `search returns empty list when no results`() = runTest {
        coEvery { api.searchManga(query = any()) } returns KitsuPagedResponse(data = emptyList())

        val results = tracker.search("NonExistent12345")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `search handles resource id with non-numeric string gracefully`() = runTest {
        val resource = KitsuResource(
            id = "not-a-number",
            type = "manga",
            attributes = KitsuAttributes(canonicalTitle = "Test")
        )
        coEvery { api.searchManga(query = any()) } returns KitsuPagedResponse(data = listOf(resource))

        val results = tracker.search("Test")

        assertEquals(1, results.size)
        assertEquals(0L, results[0].remoteId) // toLongOrNull() ?: 0L
    }

    @Test
    fun `search propagates network exception`() = runTest {
        coEvery { api.searchManga(query = any()) } throws RuntimeException("Network error")

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
    fun `find returns null when userId is not set`() = runTest {
        // tracker not logged in, no userId
        val entry = tracker.find(42L)

        assertNull(entry)
        coVerify(exactly = 0) { api.findLibraryEntry(any(), any()) }
    }

    @Test
    fun `find returns TrackEntry when library entry exists`() = runTest {
        loginTracker()
        val attrs = KitsuAttributes(
            status = "current",
            progressedChapters = 50,
            ratingTwenty = 16
        )
        val libraryEntry = KitsuResource(id = "99", type = "libraryEntries", attributes = attrs)
        coEvery { api.findLibraryEntry(mangaId = 42L, userId = 77L) } returns KitsuPagedResponse(
            data = listOf(libraryEntry)
        )

        val entry = tracker.find(42L)

        assertNotNull(entry)
        assertEquals(42L, entry!!.remoteId)
        assertEquals(TrackStatus.READING, entry.status)
        assertEquals(50f, entry.lastChapterRead)
        assertEquals(8f, entry.score) // ratingTwenty=16 → 16/2=8
    }

    @Test
    fun `find returns null when library entry list is empty`() = runTest {
        loginTracker()
        coEvery { api.findLibraryEntry(any(), any()) } returns KitsuPagedResponse(data = emptyList())

        val entry = tracker.find(42L)

        assertNull(entry)
    }

    @Test
    fun `find propagates network error as exception`() = runTest {
        loginTracker()
        coEvery { api.findLibraryEntry(any(), any()) } throws RuntimeException("HTTP 500")

        var threwException = false
        try {
            tracker.find(42L)
        } catch (e: RuntimeException) {
            threwException = true
        }

        // KitsuTracker.find() does not wrap in try/catch, so exceptions propagate to the caller
        assertTrue(threwException)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `update creates new library entry when none exists`() = runTest {
        loginTracker()
        coEvery {
            api.findLibraryEntry(mangaId = 42L, userId = 77L)
        } returns KitsuPagedResponse(data = emptyList())

        val dummyResponse = KitsuLibraryEntryResponse(
            data = KitsuResource(id = "1", type = "libraryEntries", attributes = KitsuAttributes(status = "current"))
        )
        coEvery { api.createLibraryEntry(any()) } returns dummyResponse

        val entry = TrackEntry(
            remoteId = 42L,
            mangaId = 1L,
            trackerId = TrackerType.KITSU,
            status = TrackStatus.READING,
            lastChapterRead = 10f,
            score = 7f
        )

        val result = tracker.update(entry)

        assertEquals(entry, result)
        coVerify(exactly = 1) { api.createLibraryEntry(any()) }
        coVerify(exactly = 0) { api.updateLibraryEntry(any(), any()) }
    }

    @Test
    fun `update patches existing library entry when one exists`() = runTest {
        loginTracker()
        val existingEntry = KitsuResource(
            id = "99",
            type = "libraryEntries",
            attributes = KitsuAttributes(status = "current", progressedChapters = 5)
        )
        coEvery { api.findLibraryEntry(any(), any()) } returns KitsuPagedResponse(data = listOf(existingEntry))

        val dummyResponse = KitsuLibraryEntryResponse(data = existingEntry)
        coEvery { api.updateLibraryEntry(99L, any()) } returns dummyResponse

        val entry = TrackEntry(
            remoteId = 42L, mangaId = 1L, trackerId = TrackerType.KITSU,
            status = TrackStatus.COMPLETED, lastChapterRead = 100f, score = 9f
        )

        val result = tracker.update(entry)

        assertEquals(entry, result)
        coVerify(exactly = 1) { api.updateLibraryEntry(99L, any()) }
        coVerify(exactly = 0) { api.createLibraryEntry(any()) }
    }

    @Test
    fun `update returns entry unchanged when userId not set`() = runTest {
        val entry = TrackEntry(
            remoteId = 42L, mangaId = 1L, trackerId = TrackerType.KITSU,
            status = TrackStatus.READING
        )

        val result = tracker.update(entry)

        assertEquals(entry, result)
        coVerify(exactly = 0) { api.createLibraryEntry(any()) }
        coVerify(exactly = 0) { api.updateLibraryEntry(any(), any()) }
    }

    @Test
    fun `update converts score from 0-10 scale to ratingTwenty 0-20 scale`() = runTest {
        loginTracker()
        coEvery { api.findLibraryEntry(any(), any()) } returns KitsuPagedResponse(data = emptyList())

        var capturedRatingTwenty: Int? = null
        coEvery { api.createLibraryEntry(any()) } answers {
            capturedRatingTwenty = firstArg<app.otakureader.data.tracking.api.KitsuLibraryEntryRequest>()
                .data.attributes.ratingTwenty
            KitsuLibraryEntryResponse(
                data = KitsuResource(id = "1", type = "libraryEntries", attributes = KitsuAttributes(status = "current"))
            )
        }

        val entry = TrackEntry(
            remoteId = 42L, mangaId = 1L, trackerId = TrackerType.KITSU,
            status = TrackStatus.READING, score = 8f
        )
        tracker.update(entry)

        assertEquals(16, capturedRatingTwenty) // 8 * 2 = 16
    }

    @Test
    fun `update sets ratingTwenty to null when score is zero`() = runTest {
        loginTracker()
        coEvery { api.findLibraryEntry(any(), any()) } returns KitsuPagedResponse(data = emptyList())

        var capturedRatingTwenty: Int? = -1
        coEvery { api.createLibraryEntry(any()) } answers {
            capturedRatingTwenty = firstArg<app.otakureader.data.tracking.api.KitsuLibraryEntryRequest>()
                .data.attributes.ratingTwenty
            KitsuLibraryEntryResponse(
                data = KitsuResource(id = "1", type = "libraryEntries", attributes = KitsuAttributes(status = "current"))
            )
        }

        val entry = TrackEntry(
            remoteId = 42L, mangaId = 1L, trackerId = TrackerType.KITSU,
            status = TrackStatus.READING, score = 0f
        )
        tracker.update(entry)

        assertNull(capturedRatingTwenty)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping — remote string → TrackStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `statusFromKitsu maps current to READING`() = runTest {
        assertEquals(TrackStatus.READING, findWithStatus("current")?.status)
    }

    @Test
    fun `statusFromKitsu maps completed to COMPLETED`() = runTest {
        assertEquals(TrackStatus.COMPLETED, findWithStatus("completed")?.status)
    }

    @Test
    fun `statusFromKitsu maps on_hold to ON_HOLD`() = runTest {
        assertEquals(TrackStatus.ON_HOLD, findWithStatus("on_hold")?.status)
    }

    @Test
    fun `statusFromKitsu maps dropped to DROPPED`() = runTest {
        assertEquals(TrackStatus.DROPPED, findWithStatus("dropped")?.status)
    }

    @Test
    fun `statusFromKitsu maps planned to PLAN_TO_READ`() = runTest {
        assertEquals(TrackStatus.PLAN_TO_READ, findWithStatus("planned")?.status)
    }

    @Test
    fun `statusFromKitsu maps unknown string to PLAN_TO_READ`() = runTest {
        assertEquals(TrackStatus.PLAN_TO_READ, findWithStatus("unknown_value")?.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping — TrackStatus → remote string
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `statusToKitsu maps RE_READING to current`() = runTest {
        loginTracker()
        coEvery { api.findLibraryEntry(any(), any()) } returns KitsuPagedResponse(data = emptyList())

        var capturedStatus: String? = null
        coEvery { api.createLibraryEntry(any()) } answers {
            capturedStatus = firstArg<app.otakureader.data.tracking.api.KitsuLibraryEntryRequest>()
                .data.attributes.status
            KitsuLibraryEntryResponse(
                data = KitsuResource(id = "1", type = "libraryEntries", attributes = KitsuAttributes(status = "current"))
            )
        }

        tracker.update(
            TrackEntry(
                remoteId = 1L, mangaId = 1L, trackerId = TrackerType.KITSU,
                status = TrackStatus.RE_READING
            )
        )

        assertEquals("current", capturedStatus)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // toTrackStatus / toRemoteStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toTrackStatus converts ordinal to TrackStatus`() {
        assertEquals(TrackStatus.READING, tracker.toTrackStatus(TrackStatus.READING.ordinal))
        assertEquals(TrackStatus.COMPLETED, tracker.toTrackStatus(TrackStatus.COMPLETED.ordinal))
        assertEquals(TrackStatus.ON_HOLD, tracker.toTrackStatus(TrackStatus.ON_HOLD.ordinal))
        assertEquals(TrackStatus.DROPPED, tracker.toTrackStatus(TrackStatus.DROPPED.ordinal))
        assertEquals(TrackStatus.PLAN_TO_READ, tracker.toTrackStatus(TrackStatus.PLAN_TO_READ.ordinal))
        assertEquals(TrackStatus.RE_READING, tracker.toTrackStatus(TrackStatus.RE_READING.ordinal))
    }

    @Test
    fun `toRemoteStatus converts TrackStatus to ordinal`() {
        TrackStatus.entries.forEach { status ->
            assertEquals(status.ordinal, tracker.toRemoteStatus(status))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun loginTracker() {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } returns userResponse(id = "77")
        tracker.login(username = "user@example.com", password = "pass")
    }

    private suspend fun findWithStatus(status: String): TrackEntry? {
        loginTracker()
        val attrs = KitsuAttributes(status = status, progressedChapters = 0, ratingTwenty = null)
        val libraryEntry = KitsuResource(id = "1", type = "libraryEntries", attributes = attrs)
        coEvery { api.findLibraryEntry(any(), any()) } returns KitsuPagedResponse(data = listOf(libraryEntry))
        return tracker.find(42L)
    }
}
