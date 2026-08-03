package app.otakureader.feature.tracking

import app.otakureader.core.preferences.PendingOAuthSession
import app.otakureader.core.preferences.PendingOAuthStore
import app.otakureader.domain.tracking.TrackManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the CSRF state check on the OAuth callback.
 *
 * The check exists because the callback is a deep link: anything on the device that can open a
 * `app.otakureader://…-oauth` URL reaches this code. The state is what separates a redirect this
 * app asked for from one someone else fabricated — and for AniList's implicit grant, where the
 * access token itself rides in the redirect, accepting a fabricated one means logging the user
 * into an account of the attacker's choosing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackerOAuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var trackManager: TrackManager
    private lateinit var pendingOAuthStore: PendingOAuthStore

    private val session = PendingOAuthSession(
        trackerId = 1,
        codeVerifier = "verifier",
        state = "the-real-state",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trackManager = mockk(relaxed = true)
        pendingOAuthStore = mockk {
            coEvery { get() } returns session
            coEvery { clear() } just runs
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TrackerOAuthViewModel(trackManager, pendingOAuthStore)

    @Test
    fun `a matching state proceeds to the token exchange`() = runTest(testDispatcher) {
        coEvery { trackManager.login(any(), any(), any()) } returns true
        val vm = viewModel()

        vm.exchangeCode("anilist", "the-code", "the-real-state")
        advanceUntilIdle()

        assertTrue(vm.state.value.success)
        coVerify { trackManager.login("anilist", "the-code", "verifier") }
    }

    @Test
    fun `a mismatched state is rejected before any exchange`() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.exchangeCode("anilist", "the-code", "some-other-state")
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.success)
        // Asserting the error alone would not prove much: the point is that the credential never
        // leaves the device on a callback this app cannot vouch for.
        coVerify(exactly = 0) { trackManager.login(any(), any(), any()) }
    }

    /**
     * The case the previous condition let through.
     *
     * It began with `callbackState != null`, so a callback carrying no state at all skipped the
     * comparison and went straight to the exchange — the exact shape a fabricated deep link takes,
     * since an attacker simply omits the parameter they cannot guess. A stored session always has
     * a state, so "no state came back" can only mean this is not the login that was started.
     */
    @Test
    fun `a missing state is rejected rather than treated as an absent check`() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.exchangeCode("anilist", "attacker-supplied-token", null)
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.success)
        coVerify(exactly = 0) { trackManager.login(any(), any(), any()) }
    }
}
