package app.otakureader.data.download

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import app.otakureader.core.network.RequestCategory
import app.otakureader.core.network.di.PageImageOkHttp
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
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("HTTP ${response.code}: ${response.message}")
                        }
                        val body = checkNotNull(response.body) { "Empty response body for $url" }
                        destFile.outputStream().use { out ->
                            body.byteStream().use { it.copyTo(out) }
                        }
                    }
                    return@withContext Result.success(destFile)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    attempt++
                    if (attempt < MAX_RETRIES) {
                        delay(RETRY_BASE_DELAY_MS * (1 shl (attempt - 1)))
                    }
                }
            }
            Result.failure(lastError ?: Exception("Download failed"))
        }

    private companion object {
        const val MAX_RETRIES = 3
        const val RETRY_BASE_DELAY_MS = 1_000L
    }
}
