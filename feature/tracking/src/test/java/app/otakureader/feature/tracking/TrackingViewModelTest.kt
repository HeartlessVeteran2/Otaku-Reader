package app.otakureader.feature.tracking

import android.content.Context
import app.cash.turbine.test
import app.otakureader.core.preferences.PendingOAuthStore
import app.otakureader.domain.model.SyncStatus
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerSyncState
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.repository.TrackerSyncRepository
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.domain.tracking.Tracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var trackRepository: TrackRepository
    private lateinit var trackerSyncRepository: TrackerSyncRepository
    private lateinit var pendingOAuthStore: PendingOAuthStore
    private lateinit var context: Context
    private lateinit var mockTracker: Tracker

    private val trackerId = TrackerType.ANILIST

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockTracker = mockk {
            every { id } returns trackerId
            every { name } returns "AniList"
            every { isLoggedIn } returns false
        }

        trackRepository = mockk {
            every { observeEntriesForManga(any()) } returns flowOf(emptyList())
        }
        trackerSyncRepository = mockk {
            every { getSyncStateForManga(any()) } returns flowOf(emptyList())
        }
        pendingOAuthStore = mockk {
            coEvery { save(any(), any(), any()) } just runs
        }
        context = mockk {
            every { getString(any<Int>()) } returns ""
            every { getString(any<Int>(), *anyVararg()) } returns ""
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        trackers: Set<Tracker> = setOf(mockTracker)
    ) = TrackingViewModel(
        trackers = trackers,
        trackRepository = trackRepository,
        trackerSyncRepository = trackerSyncRepository,
        pendingOAuthStore = pendingOAuthStore,
        context = context
    )

    @Test
    fun init_stateIsEmpty() {
        val viewModel = createViewModel()
        assertTrue(viewModel.state.value.trackers.isEmpty())
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun onEvent_LoadTrackers_populatesTrackerList() = runTest {
        every { trackRepository.observeEntriesForManga(10L) } returns flowOf(emptyList())
        every { trackerSyncRepository.getSyncStateForManga(10L) } returns flowOf(emptyList())

        val viewModel = createViewModel()
        // Subscribe so stateIn(WhileSubscribed) starts collecting from _state.
        backgroundScope.launch { viewModel.state.collect { } }
        viewModel.onEvent(TrackingEvent.LoadTrackers(mangaId = 10L, mangaTitle = "Naruto"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.trackers.size)
        assertEquals("AniList", viewModel.state.value.trackers[0].name)
        assertEquals(10L, viewModel.state.value.mangaId)
        assertEquals("Naruto", viewModel.state.value.mangaTitle)
    }

    @Test
    fun onEvent_LoadTrackers_setsLoggedInStatus() = runTest {
        every { mockTracker.isLoggedIn } returns true
        every { trackRepository.observeEntriesForManga(any()) } returns flowOf(emptyList())
        every { trackerSyncRepository.getSyncStateForManga(any()) } returns flowOf(emptyList())

        val viewModel = createViewModel()
        // Subscribe so stateIn(WhileSubscribed) starts collecting from _state.
        backgroundScope.launch { viewModel.state.collect { } }
        viewModel.onEvent(TrackingEvent.LoadTrackers(mangaId = 1L, mangaTitle = "Test"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.trackers[0].isLoggedIn)
    }

    @Test
    fun onEvent_InitiateLogin_oauthTracker_emitsOpenOAuthEffect() = runTest {
        // android.util.Base64 is not available in JVM unit tests — mock the static method
        // so that generateCodeVerifier() doesn't throw RuntimeException.
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "mocked_code_verifier"

        val urlState = slot<String>()
        val savedState = slot<String>()
        coEvery { pendingOAuthStore.save(any(), any(), capture(savedState)) } just runs

        val oauthTracker = mockk<Tracker> {
            every { id } returns TrackerType.MY_ANIME_LIST
            every { name } returns "MyAnimeList"
            every { isLoggedIn } returns false
            every { authorizationUrl(any(), capture(urlState)) } returns "https://myanimelist.net/oauth"
        }

        val viewModel = createViewModel(trackers = setOf(oauthTracker))

        try {
            viewModel.effect.test {
                viewModel.onEvent(TrackingEvent.InitiateLogin(trackerId = TrackerType.MY_ANIME_LIST))
                testDispatcher.scheduler.advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is TrackingEffect.OpenOAuth)
                assertEquals(TrackerType.MY_ANIME_LIST, (effect as TrackingEffect.OpenOAuth).trackerId)
            }
        } finally {
            unmockkStatic(android.util.Base64::class)
        }

        // The state handed to the tracker must be the same one persisted for the callback to
        // compare against. Asserting only that *a* state was saved is what let the previous code
        // look correct: it generated a state and stored it, never sent it to the provider, and the
        // callback then had nothing to compare — so the CSRF guard was inert while this test and
        // every other one stayed green.
        assertTrue(savedState.isCaptured)
        assertTrue(urlState.isCaptured)
        assertEquals(savedState.captured, urlState.captured)
    }

    @Test
    fun onEvent_InitiateLogin_credentialTracker_setsLoginDialogTrackerId() = runTest {
        val kitsuTracker = mockk<Tracker> {
            every { id } returns TrackerType.KITSU
            every { name } returns "Kitsu"
            every { isLoggedIn } returns false
        }

        val viewModel = createViewModel(trackers = setOf(kitsuTracker))
        // Subscribe so stateIn(WhileSubscribed) starts collecting from _state.
        backgroundScope.launch { viewModel.state.collect { } }
        viewModel.onEvent(TrackingEvent.InitiateLogin(trackerId = TrackerType.KITSU))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(TrackerType.KITSU, viewModel.state.value.loginDialogTrackerId)
    }

    @Test
    fun onEvent_DismissLoginDialog_clearsLoginDialogTrackerId() = runTest {
        val kitsuTracker = mockk<Tracker> {
            every { id } returns TrackerType.KITSU
            every { name } returns "Kitsu"
            every { isLoggedIn } returns false
        }

        val viewModel = createViewModel(trackers = setOf(kitsuTracker))
        // Subscribe so stateIn(WhileSubscribed) starts collecting from _state.
        backgroundScope.launch { viewModel.state.collect { } }
        viewModel.onEvent(TrackingEvent.InitiateLogin(trackerId = TrackerType.KITSU))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.loginDialogTrackerId)

        viewModel.onEvent(TrackingEvent.DismissLoginDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.loginDialogTrackerId)
    }

    @Test
    fun onEvent_Login_success_emitsShowMessageEffect() = runTest {
        coEvery { mockTracker.login(any(), any()) } returns true
        every { trackRepository.observeEntriesForManga(any()) } returns flowOf(emptyList())
        every { trackerSyncRepository.getSyncStateForManga(any()) } returns flowOf(emptyList())

        val viewModel = createViewModel()
        // Load trackers first so the tracker is in state
        viewModel.onEvent(TrackingEvent.LoadTrackers(mangaId = 1L, mangaTitle = "Test"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(TrackingEvent.Login(trackerId = trackerId, username = "user", password = "pass"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is TrackingEffect.ShowMessage)
        }
    }

    @Test
    fun onEvent_Login_failure_emitsShowErrorEffect() = runTest {
        coEvery { mockTracker.login(any(), any()) } returns false

        val viewModel = createViewModel()

        viewModel.effect.test {
            viewModel.onEvent(TrackingEvent.Login(trackerId = trackerId, username = "user", password = "wrong"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is TrackingEffect.ShowError)
        }
    }

    @Test
    fun onEvent_Login_throws_emitsShowErrorEffect() = runTest {
        coEvery { mockTracker.login(any(), any()) } throws RuntimeException("Network error")

        val viewModel = createViewModel()

        viewModel.effect.test {
            viewModel.onEvent(TrackingEvent.Login(trackerId = trackerId, username = "u", password = "p"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is TrackingEffect.ShowError)
        }
    }

    @Test
    fun onEvent_Logout_callsTrackerLogout() = runTest {
        every { mockTracker.logout() } just runs
        every { trackRepository.observeEntriesForManga(any()) } returns flowOf(emptyList())
        every { trackerSyncRepository.getSyncStateForManga(any()) } returns flowOf(emptyList())

        val viewModel = createViewModel()
        viewModel.onEvent(TrackingEvent.LoadTrackers(mangaId = 1L, mangaTitle = "Test"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TrackingEvent.Logout(trackerId = trackerId))
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.verify(exactly = 1) { mockTracker.logout() }
    }

    @Test
    fun onEvent_Search_success_populatesSearchResults() = runTest {
        val results = listOf(
            TrackEntry(remoteId = 1L, mangaId = 0L, trackerId = trackerId, title = "Naruto")
        )
        coEvery { mockTracker.search(any()) } returns results

        val viewModel = createViewModel()
        // Subscribe so stateIn(WhileSubscribed) starts collecting from _state.
        backgroundScope.launch { viewModel.state.collect { } }
        viewModel.onEvent(TrackingEvent.Search(trackerId = trackerId, query = "Naruto"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.searchResults.size)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun onEvent_Search_failure_emitsShowErrorEffect() = runTest {
        coEvery { mockTracker.search(any()) } throws RuntimeException("Search error")

        val viewModel = createViewModel()

        viewModel.effect.test {
            viewModel.onEvent(TrackingEvent.Search(trackerId = trackerId, query = "test"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is TrackingEffect.ShowError)
        }
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun onEvent_OnSearchQueryChange_updatesQuery() = runTest {
        val viewModel = createViewModel()
        // Subscribe so stateIn(WhileSubscribed) starts collecting from _state.
        backgroundScope.launch { viewModel.state.collect { } }
        viewModel.onEvent(TrackingEvent.OnSearchQueryChange("One Piece"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("One Piece", viewModel.state.value.searchQuery)
    }

    @Test
    fun onEvent_ClearSearch_resetsSearchState() {
        val viewModel = createViewModel()
        viewModel.onEvent(TrackingEvent.OnSearchQueryChange("test"))
        viewModel.onEvent(TrackingEvent.ClearSearch)

        assertEquals("", viewModel.state.value.searchQuery)
        assertTrue(viewModel.state.value.searchResults.isEmpty())
        assertNull(viewModel.state.value.selectedTracker)
    }

    @Test
    fun onEvent_UnlinkManga_withNoEntry_doesNothing() = runTest {
        every { trackRepository.observeEntriesForManga(any()) } returns flowOf(emptyList())
        every { trackerSyncRepository.getSyncStateForManga(any()) } returns flowOf(emptyList())

        val viewModel = createViewModel()
        viewModel.onEvent(TrackingEvent.LoadTrackers(mangaId = 1L, mangaTitle = "Test"))
        testDispatcher.scheduler.advanceUntilIdle()

        // No entry linked — should silently return
        viewModel.onEvent(TrackingEvent.UnlinkManga(trackerId = trackerId))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { trackRepository.deleteEntry(any(), any()) }
    }

    @Test
    fun onEvent_SyncTracker_onConflict_setsConflictState() = runTest {
        val syncState = mockk<TrackerSyncState>(relaxed = true)
        every { syncState.trackerId } returns trackerId
        every { syncState.localLastChapterRead } returns 5f
        every { syncState.remoteLastChapterRead } returns 10f
        every { trackerSyncRepository.getSyncStateForManga(any()) } returns flowOf(listOf(syncState))
        every { trackRepository.observeEntriesForManga(any()) } returns flowOf(emptyList())
        coEvery { trackerSyncRepository.syncManga(any(), any()) } returns TrackerSyncRepository.SyncResult(
            success = false, message = "Conflict", hasConflict = true
        )

        val viewModel = createViewModel()
        // Subscribe so stateIn(WhileSubscribed) starts collecting from _state.
        backgroundScope.launch { viewModel.state.collect { } }
        viewModel.onEvent(TrackingEvent.LoadTrackers(mangaId = 1L, mangaTitle = "Test"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(TrackingEvent.SyncTracker(trackerId = trackerId))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.conflictState)
        assertEquals(trackerId, viewModel.state.value.conflictState?.trackerId)
    }

    @Test
    fun onEvent_SyncTracker_onSuccess_emitsShowMessageEffect() = runTest {
        every { trackerSyncRepository.getSyncStateForManga(any()) } returns flowOf(emptyList())
        every { trackRepository.observeEntriesForManga(any()) } returns flowOf(emptyList())
        coEvery { trackerSyncRepository.syncManga(any(), any()) } returns TrackerSyncRepository.SyncResult(
            success = true, message = "OK"
        )

        val viewModel = createViewModel()
        viewModel.onEvent(TrackingEvent.LoadTrackers(mangaId = 1L, mangaTitle = "Test"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(TrackingEvent.SyncTracker(trackerId = trackerId))
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is TrackingEffect.ShowMessage)
        }
    }

    @Test
    fun onEvent_DismissConflict_clearsConflictState() = runTest {
        every { trackerSyncRepository.getSyncStateForManga(any()) } returns flowOf(emptyList())
        every { trackRepository.observeEntriesForManga(any()) } returns flowOf(emptyList())
        coEvery { trackerSyncRepository.syncManga(any(), any()) } returns TrackerSyncRepository.SyncResult(
            success = false, message = "Conflict", hasConflict = true
        )

        val viewModel = createViewModel()
        // Subscribe so stateIn(WhileSubscribed) starts collecting from _state.
        backgroundScope.launch { viewModel.state.collect { } }
        viewModel.onEvent(TrackingEvent.LoadTrackers(mangaId = 1L, mangaTitle = "Test"))
        viewModel.onEvent(TrackingEvent.SyncTracker(trackerId = trackerId))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.conflictState)

        viewModel.onEvent(TrackingEvent.DismissConflict)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.conflictState)
    }

    @Test
    fun onEvent_ResolveConflict_callsTrackerSyncRepositoryAndEmitsMessage() = runTest {
        every { trackerSyncRepository.getSyncStateForManga(any()) } returns flowOf(emptyList())
        every { trackRepository.observeEntriesForManga(any()) } returns flowOf(emptyList())
        coEvery { trackerSyncRepository.resolveConflict(any(), any(), any()) } just runs

        val viewModel = createViewModel()
        // Subscribe so stateIn(WhileSubscribed) starts collecting from _state.
        backgroundScope.launch { viewModel.state.collect { } }
        viewModel.onEvent(TrackingEvent.LoadTrackers(mangaId = 5L, mangaTitle = "Test"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(TrackingEvent.ResolveConflict(trackerId = trackerId, useLocal = true))
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is TrackingEffect.ShowMessage)
        }

        coVerify(exactly = 1) { trackerSyncRepository.resolveConflict(5L, trackerId, true) }
        assertNull(viewModel.state.value.conflictState)
    }
}
