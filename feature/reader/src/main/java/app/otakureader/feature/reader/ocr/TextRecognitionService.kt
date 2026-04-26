package app.otakureader.feature.reader.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Service that runs ML Kit on-device OCR on manga page bitmaps and caches the
 * extracted text per page URL (same LRU strategy as
 * [app.otakureader.feature.reader.panel.PanelDetectionService]).
 *
 * Recognition is lazy / on-demand: the caller decides which pages to OCR and when.
 * Results are cached so revisiting a page does not re-invoke ML Kit.
 */
@Singleton
class TextRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
) {
    // android.util.LruCache is not thread-safe; all reads and writes are synchronized on cacheLock.
    private val cacheLock = Any()
    private val textCache = LruCache<String, String>(CACHE_SIZE)

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Return the full recognized text for a page image URL.
     *
     * Cached results are returned immediately. On a cache miss the page bitmap is
     * loaded via Coil, passed to ML Kit, and the result stored before returning.
     *
     * @param imageUrl URL of the page image (used as cache key).
     * @return Recognized text, or an empty string if recognition fails or the URL is null.
     */
    suspend fun recognizeText(imageUrl: String?): String = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) return@withContext ""

        synchronized(cacheLock) { textCache.get(imageUrl) }?.let { return@withContext it }

        val bitmap = loadBitmapFromUrl(imageUrl) ?: return@withContext ""
        val text = runRecognition(bitmap)
        // bitmap is recycled inside runRecognition() after ML Kit completes.

        synchronized(cacheLock) { textCache.put(imageUrl, text) }
        text
    }

    /**
     * Invalidate the cached OCR result for a specific page URL.
     * Useful if the page image changes (e.g. quality switch).
     */
    fun invalidate(imageUrl: String) {
        synchronized(cacheLock) { textCache.remove(imageUrl) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun loadBitmapFromUrl(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                // Hardware-backed bitmaps cannot be read by ML Kit (pixels live on the GPU).
                .allowHardware(false)
                .build()

            when (val result = imageLoader.execute(request)) {
                is SuccessResult -> result.image.toBitmap()
                else -> null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw CancellationException to maintain structured cancellation.
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Run ML Kit text recognition on [bitmap] and return the concatenated text.
     * The bitmap is recycled inside this function after ML Kit has finished processing.
     * Returns an empty string on any failure.
     */
    private suspend fun runRecognition(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (!bitmap.isRecycled) bitmap.recycle()
                        // Check if the coroutine is still active before resuming.
                        if (cont.isActive) {
                            cont.resume(result.text)
                        }
                    }
                    .addOnFailureListener {
                        if (!bitmap.isRecycled) bitmap.recycle()
                        // Check if the coroutine is still active before resuming.
                        if (cont.isActive) {
                            cont.resume("")
                        }
                    }

                // Handle cancellation: clean up the bitmap if it hasn't been recycled yet.
                cont.invokeOnCancellation {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (!bitmap.isRecycled) bitmap.recycle()
                throw e
            } catch (e: Exception) {
                if (!bitmap.isRecycled) bitmap.recycle()
                cont.resume("")
            }
        }

    companion object {
        private const val CACHE_SIZE = 50
    }
}
