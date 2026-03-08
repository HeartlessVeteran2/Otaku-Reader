package app.otakureader.data.download

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val okHttpClient: OkHttpClient
) {

    /**
     * Downloads the image at [url] and writes its bytes to [destFile].
     *
     * @return [Result.success] carrying [destFile] on success,
     *         or [Result.failure] with the underlying exception on any error.
     */
    suspend fun downloadPage(url: String, destFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                destFile.parentFile?.mkdirs()

                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->

                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}: ${response.message}")
                    }

                    val body = checkNotNull(response.body) {
                        "Empty response body for $url"
                    }

                    destFile.outputStream().use { out ->
                        body.byteStream().use { it.copyTo(out) }
                    }

                    destFile
                }
            }
        }
}
