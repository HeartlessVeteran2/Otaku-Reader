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
 * Pending challenges are read straight off the StateFlow rather than collected. An earlier
 * version of this class published them on a replay-less SharedFlow, which meant a challenge
 * raised while the navigation layer was absent was dropped and the caller waited out its whole
 * timeout with no WebView. State also makes these tests read the real thing instead of a
 * collector whose subscription timing they had to get right.
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
        advanceUntilIdle()

        val callers = List(20) {
            async { subject.solve("example.test", "https://example.test/manga") }
        }
        advanceUntilIdle()

        assertEquals("one challenge for twenty callers", 1, subject.pendingChallenges.value.size)

        subject.completeChallenge("example.test", "foo=1; cf_clearance=abc", "UA/1.0")
        advanceUntilIdle()

        assertTrue("every caller must be released", callers.all { it.await() })
    }

    /** Different hosts are unrelated; clearing one must not release the other. */
    @Test
    fun `different hosts get their own challenges`() = runTest {
        val subject = manager(backgroundScope)
        advanceUntilIdle()

        val a = async { subject.solve("a.test", "https://a.test/x") }
        val b = async { subject.solve("b.test", "https://b.test/y") }
        advanceUntilIdle()

        assertEquals(2, subject.pendingChallenges.value.size)

        subject.completeChallenge("a.test", "foo=1; cf_clearance=abc", "UA/1.0")
        advanceUntilIdle()
        assertTrue(a.await())

        subject.completeChallenge("b.test", null, null)
        advanceUntilIdle()
        assertFalse("no cookie means no clearance", b.await())
    }

    /** A user who closes the WebView without solving must not leave callers hanging. */
    @Test
    fun `closing without cookies releases callers as unsolved`() = runTest {
        val subject = manager(backgroundScope)
        advanceUntilIdle()

        val caller = async { subject.solve("example.test", "https://example.test/manga") }
        advanceUntilIdle()

        subject.completeChallenge("example.test", cookieString = "", userAgent = null)
        advanceUntilIdle()

        assertFalse(caller.await())
    }

    /**
     * A second challenge for the same host must work after the first finished — otherwise
     * clearance expiring would leave the host permanently unsolvable.
     */
    @Test
    fun `a host can be challenged again after completing`() = runTest {
        val subject = manager(backgroundScope)
        advanceUntilIdle()

        val first = async { subject.solve("example.test", "https://example.test/1") }
        advanceUntilIdle()
        subject.completeChallenge("example.test", "foo=1; cf_clearance=abc", "UA/1.0")
        advanceUntilIdle()
        assertTrue(first.await())

        val second = async { subject.solve("example.test", "https://example.test/2") }
        advanceUntilIdle()
        assertEquals("the second challenge must be raised again", 1, subject.pendingChallenges.value.size)

        subject.completeChallenge("example.test", "cf_clearance=def", "UA/1.0")
        advanceUntilIdle()
        assertTrue(second.await())
    }

    /**
     * Success must hinge on the Cloudflare clearance cookie, not on the cookie header being
     * non-empty. The WebView reports every cookie the site set, so merely loading the page can
     * produce a session or consent cookie — which would read as clearance, release the request,
     * and store a User-Agent that earned nothing.
     */
    @Test
    fun `unrelated cookies do not count as clearance`() = runTest {
        val subject = manager(backgroundScope)
        advanceUntilIdle()

        val caller = async { subject.solve("example.test", "https://example.test/manga") }
        advanceUntilIdle()

        subject.completeChallenge("example.test", "session=xyz; consent=1", "UA/1.0")
        advanceUntilIdle()

        assertFalse("a session cookie is not clearance", caller.await())
    }

    @Test
    fun `the clearance cookie is recognised among others`() = runTest {
        val subject = manager(backgroundScope)
        advanceUntilIdle()

        val caller = async { subject.solve("example.test", "https://example.test/manga") }
        advanceUntilIdle()

        subject.completeChallenge("example.test", "a=1; cf_clearance=token; b=2", "UA/1.0")
        advanceUntilIdle()

        assertTrue(caller.await())
    }

    /** A solved host must leave the pending set, or the navigation layer would reopen it. */
    @Test
    fun `completing a challenge clears it from the pending set`() = runTest {
        val subject = manager(backgroundScope)
        advanceUntilIdle()

        val caller = async { subject.solve("example.test", "https://example.test/manga") }
        advanceUntilIdle()
        assertEquals(1, subject.pendingChallenges.value.size)

        subject.completeChallenge("example.test", "cf_clearance=abc", "UA/1.0")
        advanceUntilIdle()
        caller.await()

        assertTrue("pending must be empty once solved", subject.pendingChallenges.value.isEmpty())
    }
}
