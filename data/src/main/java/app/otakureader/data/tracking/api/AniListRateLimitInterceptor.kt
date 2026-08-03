package app.otakureader.data.tracking.api

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Waits out AniList's rate limit instead of surfacing it as a failure.
 *
 * ### Why this is needed
 *
 * AniList allows 90 requests per minute and answers the 91st with **429 Too Many Requests**. That
 * is easy to hit here without doing anything unusual: a library sync walks every tracked manga,
 * and each one is a `find` plus an `update`. Without this, the first request past the limit fails,
 * `TrackerSyncRepositoryImpl` records `SyncStatus.ERROR`, and the user sees a sync that half
 * worked — for a condition the server explicitly told us how to recover from.
 *
 * ### Reading the headers
 *
 * Two headers can say when to come back, and **they are not the same units**:
 *
 * | Header | Meaning |
 * |---|---|
 * | `Retry-After` | Seconds **from now** (a delta) |
 * | `X-RateLimit-Reset` | A Unix **timestamp** (an absolute point in time) |
 *
 * Treating a reset timestamp as a delta would sleep for decades; treating a delta as a timestamp
 * would compute a wait in the distant past and hammer the server immediately. Both are converted
 * to a delta here, and [MAX_WAIT_MS] bounds the result — a clock skewed against the server's, or a
 * header this code has misread, cannot park a request indefinitely.
 *
 * ### Why an interceptor rather than retry logic in the tracker
 *
 * Every AniList call is one `POST /graphql`, so a single interceptor covers `search`, `find` and
 * `update` at once and cannot be forgotten at a new call site. It is registered on the AniList
 * Retrofit instance only — `@TrackerOkHttp` is shared by MAL, Kitsu, Shikimori and MangaUpdates,
 * whose rate limits and headers differ.
 *
 * ### Blocking is correct here, but a plain sleep is not
 *
 * Blocking an OkHttp dispatcher thread is the intended shape: the call is already suspended from
 * the coroutine's point of view, and the thread pool exists to be occupied by in-flight calls.
 *
 * The wait still cannot be a single `Thread.sleep(waitMs)`, because **`Call.cancel()` does not
 * interrupt the thread running the interceptor chain** — it cancels the underlying exchange, and
 * OkHttp notices at the next I/O operation. There is no I/O inside a sleep. So a cancelled call
 * (which is also what a cancelled coroutine triggers, via Retrofit's suspend adapter) would sit
 * here for the rest of the wait — up to [MAX_WAIT_MS] — holding a dispatcher thread for work
 * nobody is waiting on any more.
 *
 * The wait is therefore split into [POLL_INTERVAL_MS] slices with a cancellation check between
 * them, so cancelling is noticed within that interval instead of at the end. Thread interruption
 * is honoured too, since a caller may still interrupt directly.
 */
class AniListRateLimitInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var attempt = 0

        while (response.code == HTTP_TOO_MANY_REQUESTS && attempt < MAX_RETRIES) {
            val waitMs = response.retryDelayMillis() ?: return response

            // Close before retrying. A 429 body is small but real, and an unclosed body holds its
            // connection out of the pool permanently — a leak that grows with every rate-limited
            // request and shows up much later as connection starvation.
            response.close()

            chain.awaitCancellably(waitMs)

            attempt++
            response = chain.proceed(chain.request())
        }

        return response
    }

    /**
     * Sleep for [waitMs], giving up early if the call is cancelled.
     *
     * Cancellation is checked between short slices rather than waited on, because there is nothing
     * to wait *on*: `Call.cancel()` sets a flag and cancels the exchange, and a thread inside
     * `Thread.sleep` never observes either. Polling is the mechanism OkHttp's own contract leaves
     * available — the alternative, a plain sleep, means a cancelled request keeps a dispatcher
     * thread for up to [MAX_WAIT_MS].
     *
     * The deadline is computed once from a monotonic clock, so the total wait is [waitMs] no
     * matter how the slices land — accumulating `sleep` calls would drift longer with each one.
     */
    private fun Interceptor.Chain.awaitCancellably(waitMs: Long) {
        val deadlineNanos = System.nanoTime() + waitMs * NANOS_PER_MILLI
        while (true) {
            if (call().isCanceled()) {
                throw IOException("Canceled while waiting out AniList's rate limit")
            }
            val remainingMs = (deadlineNanos - System.nanoTime()) / NANOS_PER_MILLI
            if (remainingMs <= 0) return
            try {
                Thread.sleep(minOf(POLL_INTERVAL_MS, remainingMs))
            } catch (e: InterruptedException) {
                // A direct interrupt, which is a different thing from an OkHttp cancel. Restore
                // the flag `catch` cleared so frames above still see it, then fail the call.
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while waiting out AniList's rate limit", e)
            }
        }
    }

    /**
     * How long to wait, or null when the response says nothing usable.
     *
     * Null means "give up and return the 429 as-is" rather than "wait a default". A server that
     * did not say when to come back has given no reason to believe a retry will fare better, and
     * guessing turns one rejected request into several.
     */
    private fun Response.retryDelayMillis(): Long? {
        // Retry-After first: it is a delta, so it needs no clock agreement with the server.
        header(HEADER_RETRY_AFTER)?.toLongOrNull()?.let { seconds ->
            return (seconds * MILLIS_PER_SECOND).coerceIn(MIN_WAIT_MS, MAX_WAIT_MS)
        }
        // X-RateLimit-Reset is an absolute Unix timestamp, so it must be turned into a delta
        // against our own clock. A reset already in the past yields a non-positive delta, which
        // the floor lifts to a short pause rather than an immediate re-send.
        header(HEADER_RATELIMIT_RESET)?.toLongOrNull()?.let { resetEpochSeconds ->
            val deltaMs = resetEpochSeconds * MILLIS_PER_SECOND - System.currentTimeMillis()
            return deltaMs.coerceIn(MIN_WAIT_MS, MAX_WAIT_MS)
        }
        return null
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429

        const val HEADER_RETRY_AFTER = "Retry-After"
        const val HEADER_RATELIMIT_RESET = "X-RateLimit-Reset"

        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L

        /**
         * How often the wait checks for cancellation.
         *
         * Short enough that a cancelled sync releases its thread promptly, long enough that a
         * 90-second wait costs at most a few hundred flag reads rather than a spin.
         */
        const val POLL_INTERVAL_MS = 250L

        /**
         * AniList's window is a minute, so one retry normally suffices and a second covers a
         * window boundary landing awkwardly. Beyond that the caller is better served by an error
         * it can report than by a request that appears to hang.
         */
        const val MAX_RETRIES = 2

        /** A floor, so a stale or past reset time cannot become a busy loop. */
        const val MIN_WAIT_MS = 1_000L

        /**
         * A ceiling, because the wait is only as trustworthy as the header it came from. AniList's
         * window is 60 seconds; anything asking for meaningfully longer is a misread header or a
         * clock disagreement, and blocking a dispatcher thread on it helps nobody.
         */
        const val MAX_WAIT_MS = 90_000L
    }
}
