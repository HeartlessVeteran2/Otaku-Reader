package app.otakureader.core.common.net

import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Awaits this call, aborting the underlying HTTP request when the coroutine is cancelled (#1231).
 *
 * ## Why `execute()` is not enough
 *
 * Kotlin cancellation is cooperative. Cancelling a coroutine that is blocked in
 * `Call.execute()` inside `withContext(Dispatchers.IO)` stops the *result* from being used, but
 * the request itself runs to completion and its response is then thrown away. Every keystroke in
 * a search box can therefore leave a full request in flight with nobody waiting for it — paid for
 * in bandwidth, battery and connection-pool slots.
 *
 * Enqueuing instead, and calling [Call.cancel] from `invokeOnCancellation`, makes cancellation
 * reach the socket. Nothing else changes: cancellation still flows through the coroutine
 * hierarchy exactly as before, one level deeper.
 *
 * ## What this does NOT cover
 *
 * `invokeOnCancellation` is armed only while this function is **suspended waiting for the
 * response**. Once the headers arrive the continuation resumes and the handler is gone, so
 * cancelling while the *body* is still streaming does not cancel the call. A caller that reads a
 * large body must cooperate itself — see `Downloader.copyCancellably`, where a plain
 * `InputStream.copyTo` would read a whole multi-megabyte page after the user cancelled and only
 * then notice.
 *
 * So this makes the request phase cancellable, not the transfer. For a JSON response that is the
 * whole story; for an image download it is half of it.
 *
 * ## What this is not
 *
 * Not a cancellation-token registry. The rebuild plan asked for `SourceParams(cancelToken)` plus
 * `cancelRequest(token)`, ported from a Dart codebase where structured concurrency does not exist.
 * Kotlin already gives that guarantee through `Job.cancel()`, and adding tokens would have meant
 * changing `MangaSource` — the one interface whose stability keeps published extensions working.
 * See #1231 for the full reasoning.
 *
 * The response body is closed if the continuation is cancelled after the response arrives but
 * before it is delivered; otherwise the caller owns it and must close it, exactly as with
 * `execute()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, _, _ -> response.closeQuietly() }
            }

            override fun onFailure(call: Call, e: IOException) {
                // A cancelled call fails here too. Resuming would replace the caller's
                // CancellationException with an IOException, turning "you cancelled this" into
                // "the network failed" in every log and error path above.
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        },
    )

    continuation.invokeOnCancellation {
        // Best effort: the call may already have completed, and cancelling a finished call is a
        // no-op that must not surface as a failure of the cancellation itself.
        runCatching { cancel() }
    }
}

private fun Response.closeQuietly() {
    runCatching { close() }
}
