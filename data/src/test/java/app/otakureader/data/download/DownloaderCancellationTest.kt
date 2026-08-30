package app.otakureader.data.download

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What cancelling a download must and must not do (#1231).
 *
 * The partial-file property only became reachable when the call was made cancellable. With a
 * blocking `execute()` and a non-cancellable `copyTo`, cancellation was never observed
 * mid-transfer — the request ran to completion and its result was discarded. Making cancellation
 * reach the socket is the point of the change, and it is also what exposes this.
 *
 * Real sockets and real time, so `runBlocking` rather than `runTest`: a virtual-time scheduler
 * cannot observe whether a transfer was actually interrupted.
 *
 * **Two things were tried here and deliberately dropped**, recorded so they are not re-added:
 * an `ensureActive()` in the retry loop, and a test asserting a cancelled download returns no
 * value. Both target the same property — that cancellation propagates instead of being reported
 * as a failed download — and `withContext` already guarantees it by refusing to deliver a result
 * to a cancelled coroutine. Removing the check left the test green, which is what proved it dead;
 * the test itself then cost ~200s of CI time to assert something this code does not own.
 */
class DownloaderCancellationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: Downloader

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        downloader = Downloader(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * A body that starts arriving and then trickles, so the transfer is certainly still in flight
     * when the test cancels.
     *
     * Throttled rather than delayed. A `setBodyDelay` sleeps for a fixed period, and under a
     * loaded machine that period can elapse before the cancel lands — the download then completes
     * legitimately and the assertion fails for reasons that have nothing to do with the code. This
     * version needs minutes to finish at its own pace, so "still transferring" is structural
     * rather than a bet on timing. It also unblocks the server's writer as soon as the socket
     * closes, which a sleep does not, so `shutdown` in tearDown does not hang.
     */
    private fun stalledBody() = MockResponse()
        .setBody(Buffer().apply { write(ByteArray(SLOW_BODY_BYTES)) })
        .throttleBody(THROTTLE_BYTES, THROTTLE_PERIOD_MS, TimeUnit.MILLISECONDS)

    /**
     * A cancelled transfer must not leave a truncated page behind.
     *
     * `copyTo` writes straight into the destination, and the reader decides a page is downloaded
     * by asking whether that file exists — so a short file would be served as a corrupt page for
     * good, since the chapter is already marked done and nothing re-fetches it.
     */
    @Test
    fun `cancelling a download leaves no partial file`() = runBlocking {
        server.enqueue(stalledBody())
        val dest = File(temporaryFolder.root, "partial.jpg")

        val job = launch(Dispatchers.IO) {
            downloader.downloadPage(server.url("/partial.jpg").toString(), dest)
        }
        assertNotNull(server.takeRequest(10, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertFalse("a truncated page must not be left on disk", dest.exists())
    }

    /** The ordinary path still works: cancellability must not cost correctness. */
    @Test
    fun `a successful download writes the body`() = runBlocking {
        server.enqueue(MockResponse().setBody("image-bytes"))
        val dest = File(temporaryFolder.root, "ok.jpg")

        val result = downloader.downloadPage(server.url("/ok.jpg").toString(), dest)

        assertEquals(dest, result.getOrNull())
        assertEquals("image-bytes", dest.readText())
    }

    private companion object {
        const val SLOW_BODY_BYTES = 512 * 1024
        const val THROTTLE_BYTES = 512L
        const val THROTTLE_PERIOD_MS = 200L
        const val HTTP_SERVER_ERROR = 500
        const val ATTEMPTS = 3
    }
}
