package app.otakureader.data.download

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import app.otakureader.core.network.RequestCategory
import app.otakureader.core.network.di.PageImageOkHttp
import app.otakureader.core.common.net.await
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads individual page images to the local filesystem using OkHttp.
 *
 * Each call to [downloadPage] is a self-contained, cancellable suspend function.
 * It creates all necessary parent directories before writing the file.
 */
@Singleton
class Downloader @Inject constructor(
    // The page-image client, not the shared one: it attaches the Referer and any source-supplied
    // headers recorded when the page list was fetched. Without them a hotlink-protected host
    // answers 403 and the chapter saves as unopenable files — a failure that only shows up
    // offline, long after the download reported success.
    @param:PageImageOkHttp private val okHttpClient: OkHttpClient
) {

    /**
     * Downloads the image at [url] and writes its bytes to [destFile].
     *
     * Retries up to 3 times with exponential backoff (1 s, 2 s, 4 s) on transient errors.
     * Cancellation propagates immediately without retrying.
     *
     * @return [Result.success] carrying [destFile] on success,
     *         or [Result.failure] with the underlying exception after all retries are exhausted.
     */
    suspend fun downloadPage(url: String, destFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            var attempt = 0
            var lastError: Exception? = null
            while (attempt < MAX_RETRIES) {
                try {
                    destFile.parentFile?.mkdirs()
                    val request = Request.Builder().url(url)
                        .tag(RequestCategory::class.java, RequestCategory.DOWNLOAD)
                        .build()
                    okHttpClient.newCall(request).await().use { response ->
                        if (!response.isSuccessful) {
                            error("HTTP ${response.code}: ${response.message}")
                        }
                        val body = checkNotNull(response.body) { "Empty response body for $url" }
                        destFile.outputStream().use { out ->
                            body.byteStream().use { input -> copyCancellably(input, out) }
                        }
                    }
                    return@withContext Result.success(destFile)
                } catch (e: CancellationException) {
                    // Clean up before rethrowing. `copyCancellably` reports cancellation as a
                    // CancellationException, so this — not the branch below — is the path a
                    // cancelled download actually takes, and rethrowing first would skip the
                    // cleanup on exactly the case it exists for.
                    destFile.delete()
                    throw e
                } catch (e: Exception) {
                    // A cancelled call surfaces here as an IOException, not a
                    // CancellationException — OkHttp reports cancellation by failing the
                    // in-flight read, so the `catch (CancellationException)` above never sees it
                    // and this branch runs. Reachable only since the call became cancellable
                    // (#1231): a blocking execute() plus a non-cancellable copyTo meant
                    // cancellation was never observed mid-transfer.
                    //
                    // Only the cleanup is needed. An explicit `ensureActive()` was tried here and
                    // removed as provably dead: `withContext` refuses to deliver a result to a
                    // cancelled coroutine, so a cancelled download already throws rather than
                    // returning `Result.failure` — verified by removing the check and watching
                    // the test that asserts it still pass.
                    //
                    // copyTo writes straight into destFile, and `outputStream()` creates the file
                    // the moment it opens, so an interrupted transfer leaves a truncated or empty
                    // file behind. The reader decides a page is downloaded by asking whether that
                    // file exists, so leaving one serves a corrupt page for good: the chapter is
                    // already marked done and nothing re-fetches it.
                    destFile.delete()
                    lastError = e
                    attempt++
                    if (attempt < MAX_RETRIES) {
                        delay(RETRY_BASE_DELAY_MS * (1 shl (attempt - 1)))
                    }
                }
            }
            Result.failure(lastError ?: Exception("Download failed"))
        }

    /**
     * Copies [input] to [output], giving up promptly when the coroutine is cancelled.
     *
     * `copyTo` cannot be used here, and the reason is easy to miss: [await] arms
     * `invokeOnCancellation` only while it is *suspended* waiting for the response. Once the
     * headers arrive it resumes and that handler is gone, so cancelling during the body transfer
     * does not cancel the OkHttp call — a plain `copyTo` would keep reading to the end of a
     * multi-megabyte page and only then notice. Making the request cancellable is therefore
     * necessary but not sufficient; the reader has to cooperate too.
     *
     * Checking between chunks rather than mid-read is the honest limit: a read that blocks
     * forever is still bounded by OkHttp's read timeout, not by this.
     */
    private suspend fun copyCancellably(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 8 * 1024
        const val MAX_RETRIES = 3
        const val RETRY_BASE_DELAY_MS = 1_000L
    }
}
