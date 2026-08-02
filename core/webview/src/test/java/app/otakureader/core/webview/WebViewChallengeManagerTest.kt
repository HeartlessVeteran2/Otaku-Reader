package app.otakureader.core.webview

import app.otakureader.core.preferences.ChallengeUserAgentStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the coalescing and completion rules, which is where this class earns its keep.
 *
 * Note on the harness: the collector is launched in the *test* scope, not `backgroundScope`.
 * `pendingChallenge` has no replay, so a subscriber must exist before the emission — and a
 * `backgroundScope.launch` does not start under `advanceUntilIdle()`, leaving the flow with zero
 * subscribers and every assertion reading an empty list. That looked like a coalescing bug and
 * was purely a test-harness one.
 *
 * A blocked chapter fires one request per page, so every caller arrives within milliseconds of
 * every other. Getting this wrong is not subtle in the field — it is twenty stacked WebViews, or
 * nineteen requests hanging until they time out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebViewChallengeManagerTest {

    private fun manager(scope: kotlinx.coroutines.CoroutineScope): WebViewChallengeManager {
        val store = mockk<ChallengeUserAgentStore>(relaxed = true)
        every { store.userAgentFor(any()) } returns null
        coEvery { store.store(any(), any()) } returns Unit
        return WebViewChallengeManager(store, scope)
    }

    /** The property that keeps one challenge from becoming twenty WebViews. */
    @Test
    fun `concurrent callers for one host produce a single challenge`() = runTest {
        val subject = manager(backgroundScope)
        val seen = mutableListOf<WebViewChallengeManager.ChallengeRequest>()
        val collector = launch { subject.pendingChallenge.collect { seen += it } }
        advanceUntilIdle()

        val callers = List(20) {
            async { subject.solve("example.test", "https://example.test/manga") }
        }
        advanceUntilIdle()

        assertEquals("one challenge for twenty callers", 1, seen.size)

        subject.completeChallenge("example.test", "cf_clearance=abc", "UA/1.0")
        advanceUntilIdle()

        assertTrue("every caller must be released", callers.all { it.await() })
        collector.cancel()
    }

    /** Different hosts are unrelated; clearing one must not release the other. */
    @Test
    fun `different hosts get their own challenges`() = runTest {
        val subject = manager(backgroundScope)
        val seen = mutableListOf<WebViewChallengeManager.ChallengeRequest>()
        val collector = launch { subject.pendingChallenge.collect { seen += it } }
        advanceUntilIdle()

        val a = async { subject.solve("a.test", "https://a.test/x") }
        val b = async { subject.solve("b.test", "https://b.test/y") }
        advanceUntilIdle()

        assertEquals(2, seen.size)

        subject.completeChallenge("a.test", "cf_clearance=abc", "UA/1.0")
        advanceUntilIdle()
        assertTrue(a.await())

        subject.completeChallenge("b.test", null, null)
        advanceUntilIdle()
        assertFalse("no cookie means no clearance", b.await())
        collector.cancel()
    }

    /** A user who closes the WebView without solving must not leave callers hanging. */
    @Test
    fun `closing without cookies releases callers as unsolved`() = runTest {
        val subject = manager(backgroundScope)
        val collector = launch { subject.pendingChallenge.collect { } }
        advanceUntilIdle()

        val caller = async { subject.solve("example.test", "https://example.test/manga") }
        advanceUntilIdle()

        subject.completeChallenge("example.test", cookieString = "", userAgent = null)
        advanceUntilIdle()

        assertFalse(caller.await())
        collector.cancel()
    }

    /**
     * A second challenge for the same host must work after the first finished — otherwise
     * clearance expiring would leave the host permanently unsolvable.
     */
    @Test
    fun `a host can be challenged again after completing`() = runTest {
        val subject = manager(backgroundScope)
        val seen = mutableListOf<WebViewChallengeManager.ChallengeRequest>()
        val collector = launch { subject.pendingChallenge.collect { seen += it } }
        advanceUntilIdle()

        val first = async { subject.solve("example.test", "https://example.test/1") }
        advanceUntilIdle()
        subject.completeChallenge("example.test", "cf_clearance=abc", "UA/1.0")
        advanceUntilIdle()
        assertTrue(first.await())

        val second = async { subject.solve("example.test", "https://example.test/2") }
        advanceUntilIdle()
        assertEquals("the second challenge must actually be raised", 2, seen.size)

        subject.completeChallenge("example.test", "cf_clearance=def", "UA/1.0")
        advanceUntilIdle()
        assertTrue(second.await())
        collector.cancel()
    }
}
