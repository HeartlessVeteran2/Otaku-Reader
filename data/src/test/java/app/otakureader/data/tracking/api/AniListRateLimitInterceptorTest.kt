package app.otakureader.data.tracking.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Covers the three things this interceptor has to get right: it retries a 429, it waits the amount
 * the server asked for, and it gives up rather than retrying forever.
 *
 * A real [MockWebServer] rather than a mocked `Chain`, because the properties under test are about
 * how many requests actually reach a server and in what order — which a mocked chain would assert
 * by construction rather than observe.
 */
class AniListRateLimitInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(AniListRateLimitInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun call() = client.newCall(Request.Builder().url(server.url("/graphql")).build()).execute()

    @Test
    fun `a 429 carrying Retry-After is retried and the retry's result is returned`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody("slow down"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{}}"""))

        val response = call()

        assertEquals(200, response.code)
        assertEquals("""{"data":{}}""", response.body!!.string())
        // Two requests reached the server: the assertion that the retry actually happened rather
        // than the interceptor having somehow returned the queued second response directly.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `the wait is at least as long as Retry-After asked for`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
        server.enqueue(MockResponse().setResponseCode(200))

        val elapsed = measureElapsed { call() }

        // Without this the test would pass on an implementation that retried instantly, which is
        // the failure mode that gets a client rate-limited harder rather than less.
        assertTrue("waited ${elapsed}ms", elapsed >= 1_000L)
    }

    /**
     * `X-RateLimit-Reset` is an absolute Unix timestamp, not a delta.
     *
     * Reading it as a delta would sleep for however many seconds have elapsed since 1970 — tens of
     * millions — so the cap is what stands between a misread header and a request that never
     * returns. Here the reset is two seconds out, and the wait must be about that, not about
     * `now`-as-seconds.
     */
    @Test
    fun `X-RateLimit-Reset is treated as a timestamp rather than a delta`() {
        val resetAt = System.currentTimeMillis() / 1_000 + 2
        server.enqueue(MockResponse().setResponseCode(429).setHeader("X-RateLimit-Reset", resetAt.toString()))
        server.enqueue(MockResponse().setResponseCode(200))

        val elapsed = measureElapsed { call() }

        assertTrue("waited ${elapsed}ms", elapsed in 1_000L..10_000L)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a reset time already in the past still pauses instead of re-sending immediately`() {
        // A clock a few seconds ahead of the server's produces a negative delta. Retrying with no
        // pause at all would spend the retry budget inside a millisecond and change nothing.
        val resetAt = System.currentTimeMillis() / 1_000 - 30
        server.enqueue(MockResponse().setResponseCode(429).setHeader("X-RateLimit-Reset", resetAt.toString()))
        server.enqueue(MockResponse().setResponseCode(200))

        var code = 0
        val elapsed = measureElapsed { code = call().code }

        assertTrue("waited ${elapsed}ms", elapsed >= 1_000L)
        assertEquals(200, code)
    }

    /**
     * The rate-limited response's body must be closed before retrying.
     *
     * An unclosed body holds its connection out of the pool for good. That is invisible at first
     * and compounds: every rate-limited request during a library sync strands one more connection,
     * and the symptom arrives much later as the pool running dry. Connection count is the way to
     * observe it — OkHttp only returns a connection to the pool once its body is finished, so a
     * leak shows up here as a second connection for what should be one reused socket.
     */
    @Test
    fun `the rate-limited response's connection is released for reuse`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody("slow down"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        call().close()

        assertEquals(1, client.connectionPool.connectionCount())
    }

    @Test
    fun `a 429 with no timing header is returned rather than retried on a guess`() {
        server.enqueue(MockResponse().setResponseCode(429))

        val response = call()

        assertEquals(429, response.code)
        // A server that did not say when to come back has given no reason to think a retry will
        // do better, and guessing turns one rejected request into several.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `retries are capped and the last 429 is returned`() {
        repeat(5) {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
        }

        val response = call()

        assertEquals(429, response.code)
        // One original attempt plus MAX_RETRIES. Asserting the count, not just the status, is what
        // distinguishes a working cap from an interceptor that never retried at all.
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a success passes through untouched`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = call()

        assertEquals(200, response.code)
        assertEquals("ok", response.body!!.string())
        assertEquals(1, server.requestCount)
    }

    /**
     * An interrupt during the wait must fail the call, not be swallowed.
     *
     * Interrupting the thread is how a cancelled call unblocks, so continuing to sleep — or
     * retrying afterwards — would leave a sync nobody is waiting on holding a dispatcher thread
     * for the rest of the rate-limit window.
     */
    @Test
    fun `an interrupt while waiting fails the call and preserves the interrupt flag`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))

        val started = CountDownLatch(1)
        var thrown: Throwable? = null
        var interruptFlagStillSet = false

        val worker = Thread {
            started.countDown()
            try {
                call()
            } catch (e: Throwable) {
                thrown = e
                interruptFlagStillSet = Thread.currentThread().isInterrupted
            }
        }
        worker.start()
        started.await(5, TimeUnit.SECONDS)
        // Give the call time to reach the sleep before interrupting it.
        Thread.sleep(500)
        worker.interrupt()
        worker.join(TimeUnit.SECONDS.toMillis(10))

        assertNotNull("expected the call to fail", thrown)
        assertTrue("expected an IOException, got $thrown", thrown is IOException)
        // `catch (InterruptedException)` clears the flag; not restoring it would hide the
        // cancellation from every frame above this one.
        assertTrue(interruptFlagStillSet)
    }

    private fun measureElapsed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    }
}
