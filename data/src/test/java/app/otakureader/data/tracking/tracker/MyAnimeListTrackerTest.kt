package app.otakureader.data.tracking.tracker

import app.otakureader.data.tracking.api.MalListStatus
import app.otakureader.data.tracking.api.MalManga
import app.otakureader.data.tracking.api.MalSearchItem
import app.otakureader.data.tracking.api.MalSearchResponse
import app.otakureader.data.tracking.api.MalTokenResponse
import app.otakureader.data.tracking.api.MyAnimeListApi
import app.otakureader.data.tracking.api.MyAnimeListOAuthApi
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
 * Unit tests for [MyAnimeListTracker] covering OAuth PKCE login/logout,
 * re-authentication, search, find, update, status mapping, and network
 * error handling.
 */
class MyAnimeListTrackerTest {

    private lateinit var oauthApi: MyAnimeListOAuthApi
    private lateinit var api: MyAnimeListApi
    private lateinit var tracker: MyAnimeListTracker

    private val clientId = "test-client-id"
    private val clientSecret = "test-client-secret"
    private val redirectUri = "app://otakureader/oauth/mal"

    private val tokenResponse = MalTokenResponse(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token",
        expiresIn = 3600L,
        tokenType = "Bearer"
    )

    @Before
    fun setUp() {
        oauthApi = mockk()
        api = mockk()
        tracker = MyAnimeListTracker(oauthApi, api, clientId, clientSecret, redirectUri)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tracker metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `tracker id matches TrackerType constant`() {
        assertEquals(TrackerType.MY_ANIME_LIST, tracker.id)
    }

    @Test
    fun `tracker name is MyAnimeList`() {
        assertEquals("MyAnimeList", tracker.name)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login — OAuth PKCE flow
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `isLoggedIn is false before login`() {
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login with valid PKCE code returns true and sets isLoggedIn`() = runTest {
        coEvery {
            oauthApi.getAccessToken(
                clientId = clientId,
                clientSecret = clientSecret,
                code = "auth-code",
                codeVerifier = "code-verifier",
                redirectUri = redirectUri
            )
        } returns tokenResponse

        val result = tracker.login(username = "code-verifier", password = "auth-code")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `login stores access and refresh tokens`() = runTest {
        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any(), any())
        } returns tokenResponse

        tracker.login(username = "verifier", password = "code")

        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `login with network error returns false and keeps isLoggedIn false`() = runTest {
        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any(), any())
        } throws RuntimeException("Network error")

        val result = tracker.login(username = "verifier", password = "bad-code")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login with HTTP 401 returns false`() = runTest {
        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any(), any())
        } throws RuntimeException("HTTP 401 Unauthorized")

        val result = tracker.login(username = "verifier", password = "expired-code")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Re-authentication
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `re-authentication after logout succeeds`() = runTest {
        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any(), any())
        } returns tokenResponse

        tracker.login(username = "verifier", password = "code")
        assertTrue(tracker.isLoggedIn)

        tracker.logout()
        assertFalse(tracker.isLoggedIn)

        val result = tracker.login(username = "verifier2", password = "code2")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `failed re-authentication leaves tracker logged out`() = runTest {
        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any(), any())
        } returns tokenResponse

        tracker.login(username = "verifier", password = "code")
        tracker.logout()

        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any(), any())
        } throws RuntimeException("Token exchange failed")

        val result = tracker.login(username = "verifier2", password = "expired-code")

        assertFalse(result)
        assertFalse(tracker.isLoggedIn)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `logout clears tokens and sets isLoggedIn to false`() = runTest {
        coEvery {
            oauthApi.getAccessToken(any(), any(), any(), any(), any(), any())
        } returns tokenResponse
        tracker.login(username = "verifier", password = "code")

        tracker.logout()

        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `logout when already logged out is safe`() {
        assertFalse(tracker.isLoggedIn)
        tracker.logout()
        assertFalse(tracker.isLoggedIn)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `search returns mapped TrackEntry list`() = runTest {
        val manga = MalManga(id = 42L, title = "Berserk", numChapters = 364)
        coEvery { api.searchManga(query = "Berserk") } returns MalSearchResponse(
            data = listOf(MalSearchItem(node = manga))
        )

        val results = tracker.search("Berserk")

        assertEquals(1, results.size)
        assertEquals(42L, results[0].remoteId)
        assertEquals("Berserk", results[0].title)
        assertEquals(364, results[0].totalChapters)
        assertEquals(TrackerType.MY_ANIME_LIST, results[0].trackerId)
        assertTrue(results[0].remoteUrl.contains("42"))
    }

    @Test
    fun `search returns empty list when no results`() = runTest {
        coEvery { api.searchManga(query = any()) } returns MalSearchResponse(data = emptyList())

        val results = tracker.search("NonExistentManga12345")

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
    fun `find returns TrackEntry when list status is present`() = runTest {
        val malManga = MalManga(
            id = 42L,
            title = "Berserk",
            numChapters = 364,
            listStatus = MalListStatus(
                status = "reading",
                numChaptersRead = 100,
                score = 9
            )
        )
        coEvery { api.getManga(42L) } returns malManga

        val entry = tracker.find(42L)

        assertNotNull(entry)
        assertEquals(42L, entry!!.remoteId)
        assertEquals("Berserk", entry.title)
        assertEquals(TrackStatus.READING, entry.status)
        assertEquals(100f, entry.lastChapterRead)
        assertEquals(9f, entry.score)
    }

    @Test
    fun `find returns null when manga has no list status`() = runTest {
        val malManga = MalManga(id = 42L, title = "Berserk", numChapters = 364, listStatus = null)
        coEvery { api.getManga(42L) } returns malManga

        val entry = tracker.find(42L)

        assertNull(entry)
    }

    @Test
    fun `find returns null on network error`() = runTest {
        coEvery { api.getManga(any()) } throws RuntimeException("HTTP 404")

        val entry = tracker.find(99999L)

        assertNull(entry)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `update calls API with correct parameters and returns entry`() = runTest {
        val entry = TrackEntry(
            remoteId = 42L,
            mangaId = 1L,
            trackerId = TrackerType.MY_ANIME_LIST,
            status = TrackStatus.COMPLETED,
            lastChapterRead = 364f,
            score = 10f
        )
        coEvery {
            api.updateListStatus(
                id = 42L,
                status = "completed",
                chaptersRead = 364,
                score = 10
            )
        } returns MalListStatus(status = "completed", numChaptersRead = 364, score = 10)

        val result = tracker.update(entry)

        assertEquals(entry, result)
        coVerify(exactly = 1) {
            api.updateListStatus(id = 42L, status = "completed", chaptersRead = 364, score = 10)
        }
    }

    @Test
    fun `update returns entry unchanged on network error`() = runTest {
        val entry = TrackEntry(
            remoteId = 42L,
            mangaId = 1L,
            trackerId = TrackerType.MY_ANIME_LIST,
            status = TrackStatus.READING,
            lastChapterRead = 50f,
            score = 8f
        )
        coEvery { api.updateListStatus(any(), any(), any(), any()) } throws RuntimeException("Network error")

        val result = tracker.update(entry)

        assertEquals(entry, result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping — remote string → TrackStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `statusFromMal maps reading to READING`() = runTest {
        val malManga = MalManga(
            id = 1L, title = "Test", numChapters = 0,
            listStatus = MalListStatus(status = "reading")
        )
        coEvery { api.getManga(1L) } returns malManga

        val entry = tracker.find(1L)

        assertEquals(TrackStatus.READING, entry?.status)
    }

    @Test
    fun `statusFromMal maps completed to COMPLETED`() = runTest {
        val malManga = MalManga(
            id = 1L, title = "Test", numChapters = 0,
            listStatus = MalListStatus(status = "completed")
        )
        coEvery { api.getManga(1L) } returns malManga

        assertEquals(TrackStatus.COMPLETED, tracker.find(1L)?.status)
    }

    @Test
    fun `statusFromMal maps on_hold to ON_HOLD`() = runTest {
        val malManga = MalManga(
            id = 1L, title = "Test", numChapters = 0,
            listStatus = MalListStatus(status = "on_hold")
        )
        coEvery { api.getManga(1L) } returns malManga

        assertEquals(TrackStatus.ON_HOLD, tracker.find(1L)?.status)
    }

    @Test
    fun `statusFromMal maps dropped to DROPPED`() = runTest {
        val malManga = MalManga(
            id = 1L, title = "Test", numChapters = 0,
            listStatus = MalListStatus(status = "dropped")
        )
        coEvery { api.getManga(1L) } returns malManga

        assertEquals(TrackStatus.DROPPED, tracker.find(1L)?.status)
    }

    @Test
    fun `statusFromMal maps plan_to_read to PLAN_TO_READ`() = runTest {
        val malManga = MalManga(
            id = 1L, title = "Test", numChapters = 0,
            listStatus = MalListStatus(status = "plan_to_read")
        )
        coEvery { api.getManga(1L) } returns malManga

        assertEquals(TrackStatus.PLAN_TO_READ, tracker.find(1L)?.status)
    }

    @Test
    fun `statusFromMal maps unknown string to PLAN_TO_READ`() = runTest {
        val malManga = MalManga(
            id = 1L, title = "Test", numChapters = 0,
            listStatus = MalListStatus(status = "unknown_status")
        )
        coEvery { api.getManga(1L) } returns malManga

        assertEquals(TrackStatus.PLAN_TO_READ, tracker.find(1L)?.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping — TrackStatus → remote string (via update)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `update sends reading status as reading string`() = runTest {
        val entry = TrackEntry(
            remoteId = 1L, mangaId = 1L, trackerId = TrackerType.MY_ANIME_LIST,
            status = TrackStatus.READING
        )
        coEvery { api.updateListStatus(any(), any(), any(), any()) } returns MalListStatus()

        tracker.update(entry)

        coVerify { api.updateListStatus(id = 1L, status = "reading", chaptersRead = 0, score = 0) }
    }

    @Test
    fun `update maps RE_READING to reading for MAL`() = runTest {
        val entry = TrackEntry(
            remoteId = 1L, mangaId = 1L, trackerId = TrackerType.MY_ANIME_LIST,
            status = TrackStatus.RE_READING
        )
        coEvery { api.updateListStatus(any(), any(), any(), any()) } returns MalListStatus()

        tracker.update(entry)

        coVerify { api.updateListStatus(id = 1L, status = "reading", chaptersRead = any(), score = any()) }
    }

    @Test
    fun `update sends on_hold status correctly`() = runTest {
        val entry = TrackEntry(
            remoteId = 1L, mangaId = 1L, trackerId = TrackerType.MY_ANIME_LIST,
            status = TrackStatus.ON_HOLD
        )
        coEvery { api.updateListStatus(any(), any(), any(), any()) } returns MalListStatus()

        tracker.update(entry)

        coVerify { api.updateListStatus(id = 1L, status = "on_hold", chaptersRead = any(), score = any()) }
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
}
