package app.otakureader.core.common.net

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers [await] (#1231).
 *
 * Real sockets and real time, deliberately: the property under test is that cancelling a coroutine
 * reaches the network, which a virtual-time scheduler cannot observe. `runBlocking`, not `runTest`.
 */
class CallExtensionsTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun call() = client.newCall(Request.Builder().url(server.url("/x")).build())

    private companion object {
        const val RESPONSE_DELAY_SECONDS = 2L
    }

    /**
     * The whole point of the change.
     *
     * `Call.execute()` blocks a thread, and cancelling the coroutine around it only discards the
     * result — the request still runs to completion. Asserting `isCanceled` looks at **state left
     * behind on the call**, which is the only thing that separates the two: a test that asserted
     * the coroutine threw `CancellationException` would pass against the blocking version too,
     * which is precisely the trap #1231 calls out.
     */
    @Test
    fun `cancelling the coroutine aborts the in-flight call`() = runBlocking {
        // Long enough that the request is certainly still open when we cancel, short enough that
        // MockWebServer's own shutdown does not give up waiting for the worker in tearDown.
        server.enqueue(MockResponse().setHeadersDelay(RESPONSE_DELAY_SECONDS, TimeUnit.SECONDS))
        val call = call()

        val job = launch(Dispatchers.IO) { call.await().close() }
        // Block until the server has actually received it, so the cancel cannot land first.
        assertNotNull("the request must reach the server before we cancel", server.takeRequest(10, TimeUnit.SECONDS))

        job.cancelAndJoin()

        assertTrue("the underlying OkHttp call must be cancelled, not merely abandoned", call.isCanceled())
    }

    /** The ordinary path still works: cancellability must not cost correctness. */
    @Test
    fun `a normal request returns its body`() = runBlocking {
        server.enqueue(MockResponse().setBody("hello"))

        val body = call().await().use { it.body?.string() }

        assertEquals("hello", body)
        assertFalse(call().isCanceled())
    }

    /**
     * A failure is delivered as the `IOException` it is.
     *
     * The `isCancelled` guard in `onFailure` exists so a *cancelled* call does not resume here —
     * OkHttp reports cancellation through the same callback, and resuming would replace the
     * caller's `CancellationException` with an `IOException`, relabelling "you cancelled this" as
     * "the network failed" everywhere above. This asserts the guard does not swallow real
     * failures on the way.
     */
    @Test
    fun `a connection failure surfaces as an IOException`() = runBlocking {
        val deadUrl = server.url("/dead")
        server.shutdown()
        val call = client.newCall(Request.Builder().url(deadUrl).build())

        val thrown = CompletableDeferred<Throwable?>()
        val job = launch(Dispatchers.IO) {
            thrown.complete(runCatching { call.await().close() }.exceptionOrNull())
        }
        job.join()

        assertTrue(
            "expected an IOException, got ${thrown.getCompleted()}",
            thrown.getCompleted() is java.io.IOException,
        )
    }
}
