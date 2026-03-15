package app.otakureader.data.tracking.tracker

import app.otakureader.data.tracking.api.ShikimoriApi
import app.otakureader.data.tracking.api.ShikimoriManga
import app.otakureader.data.tracking.api.ShikimoriOAuthApi
import app.otakureader.data.tracking.api.ShikimoriTokenResponse
import app.otakureader.data.tracking.api.ShikimoriUser
import app.otakureader.data.tracking.api.ShikimoriUserRate
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
 * Unit tests for [ShikimoriTracker] covering OAuth authorization-code login/logout,
 * re-authentication, search, find, update, status mapping, and network error handling.
 */
class ShikimoriTrackerTest {

    private lateinit var oauthApi: ShikimoriOAuthApi
    private lateinit var api: ShikimoriApi
    private lateinit var tracker: ShikimoriTracker

    private val clientId = "shikimori-client-id"
    private val clientSecret = "shikimori-client-secret"
    private val redirectUri = "app://otakureader/oauth/shikimori"

    private val tokenResponse = ShikimoriTokenResponse(
        accessToken = "shikimori-access-token",
        refreshToken = "shikimori-refresh-token",
        expiresIn = 3600L,
        tokenType = "Bearer"
    )

    private val testUser = ShikimoriUser(id = 42L, nickname = "testuser")

    @Before
    fun setUp() {
        oauthApi = mockk()
        api = mockk()
        tracker = ShikimoriTracker(oauthApi, api, clientId, clientSecret, redirectUri)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tracker metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `tracker id matches TrackerType constant`() {
        assertEquals(TrackerType.SHIKIMORI, tracker.id)
    }

    @Test
    fun `tracker name is Shikimori`() {
        assertEquals("Shikimori", tracker.name)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login — OAuth authorization-code flow
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `isLoggedIn is false before login`() {
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login with valid auth code returns true and sets isLoggedIn`() = runTest {
        coEvery {
            oauthApi.getAccessToken(
                clientId = clientId,
                clientSecret = clientSecret,
                code = "auth-code",
                redirectUri = redirectUri
            )
        } returns tokenResponse
        coEvery { api.getCurrentUser() } returns testUser

        val result = tracker.login(username = "", password = "auth-code")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `login username parameter is unused for OAuth flow`() = runTest {
        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any())
        } returns tokenResponse
        coEvery { api.getCurrentUser() } returns testUser

        val result = tracker.login(username = "ignored-value", password = "auth-code")

        assertTrue(result)
    }

    @Test
    fun `login returns false when getCurrentUser throws exception`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } throws RuntimeException("Server error")

        val result = tracker.login(username = "", password = "auth-code")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login returns false and clears tokens on OAuth network error`() = runTest {
        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any())
        } throws RuntimeException("Network error")

        val result = tracker.login(username = "", password = "invalid-code")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login returns false on HTTP 401 Unauthorized`() = runTest {
        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any())
        } throws RuntimeException("HTTP 401 Unauthorized")

        val result = tracker.login(username = "", password = "expired-code")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Re-authentication
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `re-authentication after logout succeeds`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } returns testUser

        tracker.login(username = "", password = "code-1")
        assertTrue(tracker.isLoggedIn)
        tracker.logout()
        assertFalse(tracker.isLoggedIn)

        val result = tracker.login(username = "", password = "code-2")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `failed re-authentication leaves tracker logged out`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } returns testUser
        tracker.login(username = "", password = "code-1")
        tracker.logout()

        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any())
        } throws RuntimeException("401")

        val result = tracker.login(username = "", password = "stale-code")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `provider migration — find after re-login uses new userId`() = runTest {
        coEvery { oauthApi.getAccessToken(any(), any(), any(), any(), any()) } returns tokenResponse
        coEvery { api.getCurrentUser() } returns ShikimoriUser(id = 10L, nickname = "user-a")
        tracker.login(username = "", password = "code-a")

        tracker.logout()

        coEvery { api.getCurrentUser() } returns ShikimoriUser(id = 20L, nickname = "user-b")
        tracker.login(username = "", password = "code-b")

        // After re-login with userId=20, find() should call the API with the new userId
        val userRate = ShikimoriUserRate(
            id = 1L, userId = 20L, targetId = 10L,
            targetType = "Manga", status = "watching", score = 0, chapters = 0
        )
        val manga = ShikimoriManga(id = 10L, name = "Test", url = "/mangas/10", chapters = 10)
        coEvery { api.getUserRate(userId = 20L, targetId = 10L) } returns listOf(userRate)
        coEvery { api.getManga(10L) } returns manga

        val entry = tracker.find(10L)

        assertNotNull(entry)
        coVerify(exactly = 1) { api.getUserRate(userId = 20L, targetId = 10L) }
        coVerify(exactly = 0) { api.getUserRate(userId = 10L, targetId = any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `logout clears tokens and userId`() = runTest {
        loginTracker()
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
        val manga = ShikimoriManga(
            id = 10L,
            name = "Vagabond",
            url = "/mangas/10-vagabond",
            chapters = 327
        )
        coEvery { api.searchManga(query = "Vagabond") } returns listOf(manga)

        val results = tracker.search("Vagabond")

        assertEquals(1, results.size)
        assertEquals(10L, results[0].remoteId)
        assertEquals("Vagabond", results[0].title)
        assertEquals(327, results[0].totalChapters)
        assertEquals(TrackerType.SHIKIMORI, results[0].trackerId)
        assertTrue(results[0].remoteUrl.contains("shikimori"))
    }

    @Test
    fun `search returns empty list when no results`() = runTest {
        coEvery { api.searchManga(query = any()) } returns emptyList()

        val results = tracker.search("NonExistent12345")

        assertTrue(results.isEmpty())
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
    fun `find returns null when userId not set`() = runTest {
        val entry = tracker.find(10L)

        assertNull(entry)
        coVerify(exactly = 0) { api.getUserRate(any(), any()) }
    }

    @Test
    fun `find returns TrackEntry when user rate exists`() = runTest {
        loginTracker()
        val userRate = ShikimoriUserRate(
            id = 1L,
            userId = 42L,
            targetId = 10L,
            targetType = "Manga",
            status = "watching",
            score = 8,
            chapters = 100
        )
        val manga = ShikimoriManga(id = 10L, name = "Vagabond", url = "/mangas/10", chapters = 327)
        coEvery { api.getUserRate(userId = 42L, targetId = 10L) } returns listOf(userRate)
        coEvery { api.getManga(10L) } returns manga

        val entry = tracker.find(10L)

        assertNotNull(entry)
        assertEquals(10L, entry!!.remoteId)
        assertEquals("Vagabond", entry.title)
        assertEquals(TrackStatus.READING, entry.status)
        assertEquals(100f, entry.lastChapterRead)
        assertEquals(8f, entry.score)
    }

    @Test
    fun `find returns null when no user rate exists`() = runTest {
        loginTracker()
        coEvery { api.getUserRate(any(), any()) } returns emptyList()

        val entry = tracker.find(10L)

        assertNull(entry)
    }

    @Test
    fun `find returns null on network error`() = runTest {
        loginTracker()
        coEvery { api.getUserRate(any(), any()) } throws RuntimeException("HTTP 503")

        val entry = tracker.find(10L)

        assertNull(entry)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `update creates new user rate when none exists`() = runTest {
        loginTracker()
        coEvery { api.getUserRate(any(), any()) } returns emptyList()
        val dummyRate = ShikimoriUserRate(id = 99L, userId = 42L, targetId = 10L, status = "watching")
        coEvery { api.createUserRate(any()) } returns dummyRate

        val entry = TrackEntry(
            remoteId = 10L,
            mangaId = 1L,
            trackerId = TrackerType.SHIKIMORI,
            status = TrackStatus.READING,
            lastChapterRead = 50f,
            score = 7f
        )

        val result = tracker.update(entry)

        assertEquals(entry, result)
        coVerify(exactly = 1) { api.createUserRate(any()) }
        coVerify(exactly = 0) { api.updateUserRate(any(), any()) }
    }

    @Test
    fun `update patches existing user rate when one exists`() = runTest {
        loginTracker()
        val existingRate = ShikimoriUserRate(id = 55L, userId = 42L, targetId = 10L, status = "watching")
        coEvery { api.getUserRate(any(), any()) } returns listOf(existingRate)
        coEvery { api.updateUserRate(55L, any()) } returns existingRate

        val entry = TrackEntry(
            remoteId = 10L,
            mangaId = 1L,
            trackerId = TrackerType.SHIKIMORI,
            status = TrackStatus.COMPLETED,
            lastChapterRead = 327f,
            score = 10f
        )

        val result = tracker.update(entry)

        assertEquals(entry, result)
        coVerify(exactly = 1) { api.updateUserRate(55L, any()) }
        coVerify(exactly = 0) { api.createUserRate(any()) }
    }

    @Test
    fun `update returns entry unchanged when userId not set`() = runTest {
        val entry = TrackEntry(
            remoteId = 10L, mangaId = 1L, trackerId = TrackerType.SHIKIMORI,
            status = TrackStatus.READING
        )

        val result = tracker.update(entry)

        assertEquals(entry, result)
        coVerify(exactly = 0) { api.createUserRate(any()) }
        coVerify(exactly = 0) { api.updateUserRate(any(), any()) }
    }

    @Test
    fun `update returns entry unchanged on network error`() = runTest {
        loginTracker()
        coEvery { api.getUserRate(any(), any()) } throws RuntimeException("Network error")

        val entry = TrackEntry(
            remoteId = 10L, mangaId = 1L, trackerId = TrackerType.SHIKIMORI,
            status = TrackStatus.READING
        )

        val result = tracker.update(entry)

        assertEquals(entry, result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping — remote string → TrackStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `statusFromShikimori maps watching to READING`() = runTest {
        assertEquals(TrackStatus.READING, findWithStatus("watching")?.status)
    }

    @Test
    fun `statusFromShikimori maps completed to COMPLETED`() = runTest {
        assertEquals(TrackStatus.COMPLETED, findWithStatus("completed")?.status)
    }

    @Test
    fun `statusFromShikimori maps on_hold to ON_HOLD`() = runTest {
        assertEquals(TrackStatus.ON_HOLD, findWithStatus("on_hold")?.status)
    }

    @Test
    fun `statusFromShikimori maps dropped to DROPPED`() = runTest {
        assertEquals(TrackStatus.DROPPED, findWithStatus("dropped")?.status)
    }

    @Test
    fun `statusFromShikimori maps planned to PLAN_TO_READ`() = runTest {
        assertEquals(TrackStatus.PLAN_TO_READ, findWithStatus("planned")?.status)
    }

    @Test
    fun `statusFromShikimori maps rewatching to RE_READING`() = runTest {
        assertEquals(TrackStatus.RE_READING, findWithStatus("rewatching")?.status)
    }

    @Test
    fun `statusFromShikimori maps unknown string to PLAN_TO_READ`() = runTest {
        assertEquals(TrackStatus.PLAN_TO_READ, findWithStatus("unknown_status")?.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping — TrackStatus → remote string (verified via update)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `update sends watching for READING status`() = runTest {
        loginTracker()
        coEvery { api.getUserRate(any(), any()) } returns emptyList()

        var capturedStatus: String? = null
        coEvery { api.createUserRate(any()) } answers {
            capturedStatus = firstArg<app.otakureader.data.tracking.api.ShikimoriUserRateRequest>()
                .userRate.status
            ShikimoriUserRate()
        }

        tracker.update(
            TrackEntry(
                remoteId = 1L, mangaId = 1L, trackerId = TrackerType.SHIKIMORI,
                status = TrackStatus.READING
            )
        )

        assertEquals("watching", capturedStatus)
    }

    @Test
    fun `update sends rewatching for RE_READING status`() = runTest {
        loginTracker()
        coEvery { api.getUserRate(any(), any()) } returns emptyList()

        var capturedStatus: String? = null
        coEvery { api.createUserRate(any()) } answers {
            capturedStatus = firstArg<app.otakureader.data.tracking.api.ShikimoriUserRateRequest>()
                .userRate.status
            ShikimoriUserRate()
        }

        tracker.update(
            TrackEntry(
                remoteId = 1L, mangaId = 1L, trackerId = TrackerType.SHIKIMORI,
                status = TrackStatus.RE_READING
            )
        )

        assertEquals("rewatching", capturedStatus)
    }

    @Test
    fun `update sends on_hold for ON_HOLD status`() = runTest {
        loginTracker()
        coEvery { api.getUserRate(any(), any()) } returns emptyList()

        var capturedStatus: String? = null
        coEvery { api.createUserRate(any()) } answers {
            capturedStatus = firstArg<app.otakureader.data.tracking.api.ShikimoriUserRateRequest>()
                .userRate.status
            ShikimoriUserRate()
        }

        tracker.update(
            TrackEntry(
                remoteId = 1L, mangaId = 1L, trackerId = TrackerType.SHIKIMORI,
                status = TrackStatus.ON_HOLD
            )
        )

        assertEquals("on_hold", capturedStatus)
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
        coEvery { api.getCurrentUser() } returns testUser
        tracker.login(username = "", password = "auth-code")
    }

    private suspend fun findWithStatus(status: String): TrackEntry? {
        loginTracker()
        val userRate = ShikimoriUserRate(
            id = 1L, userId = 42L, targetId = 10L,
            targetType = "Manga", status = status, score = 0, chapters = 0
        )
        val manga = ShikimoriManga(id = 10L, name = "Test", url = "/mangas/10", chapters = 10)
        coEvery { api.getUserRate(any(), any()) } returns listOf(userRate)
        coEvery { api.getManga(any()) } returns manga
        return tracker.find(10L)
    }
}
