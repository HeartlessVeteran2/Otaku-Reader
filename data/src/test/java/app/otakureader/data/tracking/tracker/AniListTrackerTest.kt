package app.otakureader.data.tracking.tracker

import app.otakureader.data.tracking.api.AniListApi
import app.otakureader.data.tracking.api.AniListCoverImage
import app.otakureader.data.tracking.api.AniListData
import app.otakureader.data.tracking.api.AniListGraphQlQuery
import app.otakureader.data.tracking.api.AniListMedia
import app.otakureader.data.tracking.api.AniListMediaList
import app.otakureader.data.tracking.api.AniListPage
import app.otakureader.data.tracking.api.AniListResponse
import app.otakureader.data.tracking.api.AniListTitle
import app.otakureader.core.preferences.TrackerTokenStore
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Unit tests for [AniListTracker] covering OAuth bearer-token login/logout,
 * re-authentication, GraphQL search/find/update, status mapping, and network
 * error handling.
 */
class AniListTrackerTest {

    private lateinit var api: AniListApi
    private lateinit var tokenStore: TrackerTokenStore
    private lateinit var tracker: AniListTracker

    @Before
    fun setUp() {
        api = mockk()
        tokenStore = mockk(relaxed = true)
        every { tokenStore.getTokens(any()) } returns null
        tracker = AniListTracker(api, tokenStore)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tracker metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `tracker id matches TrackerType constant`() {
        assertEquals(TrackerType.ANILIST, tracker.id)
    }

    @Test
    fun `tracker name is AniList`() {
        assertEquals("AniList", tracker.name)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login — OAuth bearer token (implicit / authorization-code)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `isLoggedIn is false before login`() {
        assertFalse(tracker.isLoggedIn)
    }

    @Test
    fun `login with bearer token returns true and sets isLoggedIn`() = runTest {
        val result = tracker.login(username = "", password = "valid-bearer-token")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `login stores bearer token`() = runTest {
        tracker.login(username = "", password = "my-token")

        assertTrue(tracker.isLoggedIn)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Re-authentication
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `re-authentication after logout succeeds`() = runTest {
        tracker.login(username = "", password = "token-1")
        assertTrue(tracker.isLoggedIn)

        tracker.logout()
        assertFalse(tracker.isLoggedIn)

        val result = tracker.login(username = "", password = "token-2")

        assertTrue(result)
        assertTrue(tracker.isLoggedIn)
    }

    @Test
    fun `successive logins with different tokens keep tracker logged in`() = runTest {
        tracker.login(username = "", password = "old-token")
        tracker.login(username = "", password = "new-token")

        assertTrue(tracker.isLoggedIn)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `logout clears token and sets isLoggedIn to false`() = runTest {
        tracker.login(username = "", password = "token")

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
    // Search (GraphQL)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `search returns mapped TrackEntry list from GraphQL response`() = runTest {
        val media = AniListMedia(
            id = 101L,
            title = AniListTitle(romaji = "Berserk", english = "Berserk"),
            chapters = 364,
            coverImage = AniListCoverImage(large = "https://img.anilist.co/101.jpg")
        )
        coEvery { api.query(any()) } returns AniListResponse(
            data = AniListData(page = AniListPage(media = listOf(media)))
        )

        val results = tracker.search("Berserk")

        assertEquals(1, results.size)
        assertEquals(101L, results[0].remoteId)
        assertEquals("Berserk", results[0].title)
        assertEquals(364, results[0].totalChapters)
        assertEquals(TrackerType.ANILIST, results[0].trackerId)
        assertTrue(results[0].remoteUrl.contains("101"))
    }

    @Test
    fun `search uses romaji title when english title is null`() = runTest {
        val media = AniListMedia(
            id = 5L,
            title = AniListTitle(romaji = "Vagabond", english = null),
            chapters = 327
        )
        coEvery { api.query(any()) } returns AniListResponse(
            data = AniListData(page = AniListPage(media = listOf(media)))
        )

        val results = tracker.search("Vagabond")

        assertEquals("Vagabond", results[0].title)
    }

    @Test
    fun `search returns empty list when page media is null`() = runTest {
        coEvery { api.query(any()) } returns AniListResponse(
            data = AniListData(page = AniListPage(media = emptyList()))
        )

        val results = tracker.search("anything")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `search returns empty list when data is null`() = runTest {
        coEvery { api.query(any()) } returns AniListResponse(data = null)

        val results = tracker.search("anything")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `search propagates network exception`() = runTest {
        coEvery { api.query(any()) } throws RuntimeException("Network error")

        var threwException = false
        try {
            tracker.search("query")
        } catch (e: RuntimeException) {
            threwException = true
        }

        assertTrue(threwException)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Find (GraphQL)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `find returns TrackEntry when media list entry is present`() = runTest {
        // 85 on AniList's POINT_100 scale, which the query asks for by name. The canonical
        // TrackEntry scale is 0-10, so this must come back as 8.5 — not 85.
        val listEntry = AniListMediaList(id = 1L, status = "CURRENT", score = 85f, progress = 50)
        val media = AniListMedia(
            id = 101L,
            title = AniListTitle(romaji = "Berserk", english = "Berserk"),
            chapters = 364,
            mediaListEntry = listEntry
        )
        coEvery { api.query(any()) } returns AniListResponse(
            data = AniListData(media = media)
        )

        val entry = tracker.find(101L)

        assertNotNull(entry)
        assertEquals(101L, entry!!.remoteId)
        assertEquals("Berserk", entry.title)
        assertEquals(TrackStatus.READING, entry.status)
        assertEquals(50f, entry.lastChapterRead)
        assertEquals(8.5f, entry.score)
    }

    /**
     * A manga the user has not added yet must still resolve.
     *
     * This test previously asserted `assertNull` — it locked in the bug rather than catching it.
     * `mediaListEntry` is null for anything not already on the user's list, and requiring it made
     * `find` fail for precisely the case that matters: you look something up *because* you want to
     * start tracking it. Media identity and list membership are separate facts, so a missing list
     * entry now means "found, not tracked yet".
     */
    @Test
    fun `find returns an untracked entry when media has no list entry`() = runTest {
        val media = AniListMedia(
            id = 101L,
            title = AniListTitle(romaji = "Berserk"),
            chapters = 364,
            mediaListEntry = null
        )
        coEvery { api.query(any()) } returns AniListResponse(
            data = AniListData(media = media)
        )

        val entry = tracker.find(101L)

        assertNotNull(entry)
        assertEquals(101L, entry!!.remoteId)
        assertEquals("Berserk", entry.title)
        // The media's own facts survive; only the per-user ones default.
        assertEquals(364, entry.totalChapters)
        assertEquals(TrackStatus.PLAN_TO_READ, entry.status)
        assertEquals(0f, entry.lastChapterRead)
        assertEquals(0f, entry.score)
    }

    @Test
    fun `find asks AniList for the score on a named scale`() = runTest {
        // Without `format:` AniList answers in whatever format the *user* picked — 0-10, 5 stars,
        // three smileys — and the response carries nothing saying which. The same number would
        // then mean different things per account, and no amount of arithmetic here could tell.
        var capturedQuery: AniListGraphQlQuery? = null
        coEvery { api.query(any()) } answers {
            capturedQuery = firstArg()
            AniListResponse(data = null)
        }

        tracker.find(1L)

        assertTrue(capturedQuery!!.query, capturedQuery!!.query.contains("score(format: POINT_100)"))
    }

    @Test
    fun `find returns null when data is null`() = runTest {
        coEvery { api.query(any()) } returns AniListResponse(data = null)

        val entry = tracker.find(101L)

        assertNull(entry)
    }

    @Test
    fun `find returns null on network error`() = runTest {
        coEvery { api.query(any()) } throws RuntimeException("HTTP 503")

        val entry = tracker.find(101L)

        assertNull(entry)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update (GraphQL mutation)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `update sends GraphQL mutation and returns entry`() = runTest {
        val entry = TrackEntry(
            remoteId = 101L,
            mangaId = 1L,
            trackerId = TrackerType.ANILIST,
            status = TrackStatus.COMPLETED,
            lastChapterRead = 364f,
            score = 9f
        )
        coEvery { api.query(any()) } returns AniListResponse(data = null)

        val result = tracker.update(entry)

        assertEquals(entry, result)
        coVerify(exactly = 1) { api.query(any()) }
    }

    /**
     * A failed push must surface, not be reported as the entry it failed to send.
     *
     * The previous version of this test asserted the opposite — that `update` returns the input
     * unchanged on a network error — which locked in the exact behaviour the `Tracker` KDoc
     * forbids. It matters because of what happens next: `TrackerSyncRepositoryImpl` writes
     * `syncStatus = SYNCED` and a `lastSuccessfulSync` timestamp on the line after this returns.
     * Swallowing the error recorded a successful sync for a request AniList never saw, and the
     * entry then looked up to date indefinitely. All three call sites are already inside `catch`
     * blocks that mark `SyncStatus.ERROR`.
     */
    @Test
    fun `update propagates a network error instead of reporting success`() = runTest {
        val entry = TrackEntry(
            remoteId = 101L,
            mangaId = 1L,
            trackerId = TrackerType.ANILIST,
            status = TrackStatus.READING,
            lastChapterRead = 100f,
            score = 7f
        )
        coEvery { api.query(any()) } throws RuntimeException("Network error")

        val thrown = try {
            tracker.update(entry)
            null
        } catch (e: RuntimeException) {
            e
        }

        assertNotNull(thrown)
        assertEquals("Network error", thrown!!.message)
    }

    @Test
    fun `update sends the score on AniList's 0-100 scale`() = runTest {
        // TrackEntry.score is 0-10 — the scale Kitsu, MAL and Shikimori already store — while
        // `scoreRaw` is 0-100. Passing it through unconverted filed a user's 8 as 8/100.
        var capturedQuery: AniListGraphQlQuery? = null
        coEvery { api.query(any()) } answers {
            capturedQuery = firstArg()
            AniListResponse(data = null)
        }

        tracker.update(
            TrackEntry(
                remoteId = 1L, mangaId = 1L, trackerId = TrackerType.ANILIST,
                status = TrackStatus.READING, score = 8.5f
            )
        )

        val scoreRaw = capturedQuery!!.variables["scoreRaw"]!!.jsonPrimitive
        assertEquals("85", scoreRaw.content)
        // Int, not a quoted string and not a Float: AniList declares scoreRaw as Int, and a
        // mis-typed variable is rejected for the whole document — the defect fixed in #1232.
        assertEquals(false, scoreRaw.isString)
        assertTrue(capturedQuery!!.query, capturedQuery!!.query.contains("${'$'}scoreRaw: Int"))
    }

    @Test
    fun `a score that does not divide evenly is rounded rather than truncated`() = runTest {
        // 0.7f * 10 is 6.9999995 in binary floating point. `toInt()` would file this as a 6.
        var capturedQuery: AniListGraphQlQuery? = null
        coEvery { api.query(any()) } answers {
            capturedQuery = firstArg()
            AniListResponse(data = null)
        }

        tracker.update(
            TrackEntry(
                remoteId = 1L, mangaId = 1L, trackerId = TrackerType.ANILIST,
                status = TrackStatus.READING, score = 0.7f
            )
        )

        assertEquals("7", capturedQuery!!.variables["scoreRaw"]!!.jsonPrimitive.content)
    }

    @Test
    fun `update returns what AniList confirmed rather than what was sent`() = runTest {
        // The mutation already asked for these fields and then threw them away, so a value the
        // server clamped or normalised was written back locally as whatever the app had guessed.
        coEvery { api.query(any()) } returns AniListResponse(
            data = AniListData(
                savedEntry = AniListMediaList(id = 7L, status = "COMPLETED", score = 90f, progress = 364)
            )
        )

        val result = tracker.update(
            TrackEntry(
                remoteId = 101L, mangaId = 1L, trackerId = TrackerType.ANILIST,
                status = TrackStatus.READING, lastChapterRead = 300f, score = 7f
            )
        )

        assertEquals(TrackStatus.COMPLETED, result.status)
        assertEquals(364f, result.lastChapterRead)
        assertEquals(9f, result.score)
    }

    @Test
    fun `a confirmation with no status keeps the status that was sent`() = runTest {
        // An absent field deserializes to "", which statusFromAniList would map through its else
        // branch to PLAN_TO_READ — silently rewriting the user's status on a successful update.
        coEvery { api.query(any()) } returns AniListResponse(
            data = AniListData(savedEntry = AniListMediaList(id = 7L, status = "", score = 90f, progress = 12))
        )

        val result = tracker.update(
            TrackEntry(
                remoteId = 101L, mangaId = 1L, trackerId = TrackerType.ANILIST,
                status = TrackStatus.READING, lastChapterRead = 12f
            )
        )

        assertEquals(TrackStatus.READING, result.status)
    }

    @Test
    fun `update mutation variables contain correct mediaId`() = runTest {
        val entry = TrackEntry(
            remoteId = 999L,
            mangaId = 1L,
            trackerId = TrackerType.ANILIST,
            status = TrackStatus.ON_HOLD
        )
        var capturedQuery: AniListGraphQlQuery? = null
        coEvery { api.query(any()) } answers {
            capturedQuery = firstArg()
            AniListResponse(data = null)
        }

        tracker.update(entry)

        assertNotNull(capturedQuery)
        // Unquoted: the old assertion expected the string "999", which is exactly the
        // mis-typing AniList rejected. `content` is "999" either way, so it is the
        // primitive's isString flag that actually distinguishes them.
        val mediaId = capturedQuery!!.variables["mediaId"]!!.jsonPrimitive
        assertEquals("999", mediaId.content)
        assertEquals(false, mediaId.isString)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping — remote string → TrackStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `statusFromAniList maps CURRENT to READING`() = runTest {
        val entry = buildFindResponse(status = "CURRENT")
        assertEquals(TrackStatus.READING, entry?.status)
    }

    @Test
    fun `statusFromAniList maps COMPLETED to COMPLETED`() = runTest {
        val entry = buildFindResponse(status = "COMPLETED")
        assertEquals(TrackStatus.COMPLETED, entry?.status)
    }

    @Test
    fun `statusFromAniList maps PAUSED to ON_HOLD`() = runTest {
        val entry = buildFindResponse(status = "PAUSED")
        assertEquals(TrackStatus.ON_HOLD, entry?.status)
    }

    @Test
    fun `statusFromAniList maps DROPPED to DROPPED`() = runTest {
        val entry = buildFindResponse(status = "DROPPED")
        assertEquals(TrackStatus.DROPPED, entry?.status)
    }

    @Test
    fun `statusFromAniList maps PLANNING to PLAN_TO_READ`() = runTest {
        val entry = buildFindResponse(status = "PLANNING")
        assertEquals(TrackStatus.PLAN_TO_READ, entry?.status)
    }

    @Test
    fun `statusFromAniList maps REPEATING to RE_READING`() = runTest {
        val entry = buildFindResponse(status = "REPEATING")
        assertEquals(TrackStatus.RE_READING, entry?.status)
    }

    @Test
    fun `statusFromAniList maps unknown string to PLAN_TO_READ`() = runTest {
        val entry = buildFindResponse(status = "UNKNOWN_STATUS")
        assertEquals(TrackStatus.PLAN_TO_READ, entry?.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping — TrackStatus → remote string
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `update sends CURRENT for READING status`() = runTest {
        var capturedQuery: AniListGraphQlQuery? = null
        coEvery { api.query(any()) } answers {
            capturedQuery = firstArg()
            AniListResponse(data = null)
        }

        tracker.update(
            TrackEntry(
                remoteId = 1L, mangaId = 1L, trackerId = TrackerType.ANILIST,
                status = TrackStatus.READING
            )
        )

        assertEquals("CURRENT", capturedQuery!!.variables["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `update sends PAUSED for ON_HOLD status`() = runTest {
        var capturedQuery: AniListGraphQlQuery? = null
        coEvery { api.query(any()) } answers {
            capturedQuery = firstArg()
            AniListResponse(data = null)
        }

        tracker.update(
            TrackEntry(
                remoteId = 1L, mangaId = 1L, trackerId = TrackerType.ANILIST,
                status = TrackStatus.ON_HOLD
            )
        )

        assertEquals("PAUSED", capturedQuery!!.variables["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `update sends PLANNING for PLAN_TO_READ status`() = runTest {
        var capturedQuery: AniListGraphQlQuery? = null
        coEvery { api.query(any()) } answers {
            capturedQuery = firstArg()
            AniListResponse(data = null)
        }

        tracker.update(
            TrackEntry(
                remoteId = 1L, mangaId = 1L, trackerId = TrackerType.ANILIST,
                status = TrackStatus.PLAN_TO_READ
            )
        )

        assertEquals("PLANNING", capturedQuery!!.variables["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `update sends REPEATING for RE_READING status`() = runTest {
        var capturedQuery: AniListGraphQlQuery? = null
        coEvery { api.query(any()) } answers {
            capturedQuery = firstArg()
            AniListResponse(data = null)
        }

        tracker.update(
            TrackEntry(
                remoteId = 1L, mangaId = 1L, trackerId = TrackerType.ANILIST,
                status = TrackStatus.RE_READING
            )
        )

        assertEquals("REPEATING", capturedQuery!!.variables["status"]!!.jsonPrimitive.content)
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

    private suspend fun buildFindResponse(status: String): TrackEntry? {
        val listEntry = AniListMediaList(id = 1L, status = status, score = 5f, progress = 0)
        val media = AniListMedia(
            id = 1L,
            title = AniListTitle(romaji = "Test"),
            chapters = 10,
            mediaListEntry = listEntry
        )
        coEvery { api.query(any()) } returns AniListResponse(
            data = AniListData(media = media)
        )
        return tracker.find(1L)
    }
}
