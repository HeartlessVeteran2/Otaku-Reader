package app.otakureader.feature.reader.viewmodel.delegate

import android.content.Context
import android.graphics.Bitmap
import app.otakureader.core.preferences.AiPreferences
import app.otakureader.domain.usecase.ai.TranslateOcrPageUseCase
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.ReaderState
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Delegate for the on-demand Gemini Vision OCR translation feature.
 *
 * Sibling to [ReaderOcrDelegate] (ML Kit, on-device, Latin-only, used for
 * in-chapter search). This delegate uses the multimodal Gemini API to translate
 * any-script text on a single page on demand.
 *
 * **Free-tier discipline**:
 * - Strictly on-demand per page — no batch scanning, never auto-triggered.
 * - At most one in-flight Vision request per page (debounces repeated taps).
 * - Bitmaps are downscaled to ≤[MAX_LONG_EDGE_PX] on the long edge and re-encoded
 *   to JPEG quality [JPEG_QUALITY] before being sent, to keep payloads small.
 * - Cached translations are returned instantly on revisit (the use case checks
 *   the cache before calling the AI).
 */
class ReaderOcrTranslationDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val aiPreferences: AiPreferences,
    private val translateOcrPageUseCase: TranslateOcrPageUseCase,
) {

    /** Per-page job map so repeated taps on the same page are debounced. */
    private val pageJobs = mutableMapOf<Int, Job>()

    /**
     * Observe AI / OCR-translation preferences and reflect them into [ReaderState].
     *
     * Combines the master AI toggle with the OCR translation feature toggle so the
     * UI can hide the translate button when the feature is unavailable.
     */
    fun observeSettings(
        scope: CoroutineScope,
        updateState: ((ReaderState) -> ReaderState) -> Unit,
    ) {
        combine(aiPreferences.aiEnabled, aiPreferences.aiOcrTranslation) { master, feature ->
            master && feature
        }
            .onEach { enabled -> updateState { it.copy(ocrTranslationEnabled = enabled) } }
            .launchIn(scope)
    }

    /**
     * Translate [page] for the given [chapterId] and write the result into [ReaderState].
     *
     * No-op if a translation job is already running for [pageIndex] (debounce).
     * No-op if [page] has no [ReaderPage.imageUrl] (locally-loaded archive pages
     * are not yet supported here — they would need a different bitmap source).
     */
    fun translatePage(
        scope: CoroutineScope,
        pageIndex: Int,
        page: ReaderPage,
        chapterId: Long,
        updateState: ((ReaderState) -> ReaderState) -> Unit,
    ) {
        val imageUrl = page.imageUrl ?: return
        if (pageJobs[pageIndex]?.isActive == true) return

        pageJobs[pageIndex] = scope.launch {
            // Set isOcrTranslating to true when starting a job
            updateState { it.copy(isOcrTranslating = true) }
            try {
                val bytes = loadAndDownscaleAsJpeg(imageUrl)
                if (bytes == null) {
                    return@launch
                }
                val targetLanguage = aiPreferences.aiOcrTargetLanguage.first()
                val result = translateOcrPageUseCase(
                    chapterId = chapterId,
                    pageIndex = pageIndex,
                    imageBytes = bytes,
                    targetLanguage = targetLanguage,
                )
                val translations = result.getOrNull().orEmpty()
                updateState { state ->
                    state.copy(
                        ocrTranslations = state.ocrTranslations + (pageIndex to translations),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                // Only set isOcrTranslating to false when all jobs are complete
                updateState { state ->
                    val hasActiveJobs = pageJobs.values.any { it.isActive && it != coroutineContext[Job] }
                    state.copy(isOcrTranslating = hasActiveJobs)
                }
            }
        }.also { job -> job.invokeOnCompletion { pageJobs.remove(pageIndex) } }
    }

    /** Cancel all in-flight translation jobs. Called on reader exit. */
    fun cancelAll() {
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
    }

    /**
     * Load [imageUrl] via Coil, downscale to ≤[MAX_LONG_EDGE_PX] on the long edge,
     * and re-encode as JPEG at [JPEG_QUALITY] to keep request payloads small.
     *
     * Returns null when the image cannot be loaded; callers fall back to "no
     * translations available" rather than treating this as an error.
     */
    private suspend fun loadAndDownscaleAsJpeg(imageUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                // Hardware bitmaps cannot be read pixel-by-pixel for re-encoding.
                .allowHardware(false)
                .build()
            val bitmap: Bitmap = when (val result = imageLoader.execute(request)) {
                is SuccessResult -> result.image.toBitmap()
                else -> return@withContext null
            }

            val scaled = downscale(bitmap, MAX_LONG_EDGE_PX)
            ByteArrayOutputStream().use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                stream.toByteArray()
            }.also {
                // Recycle the scaled copy if it is distinct from the source so we don't
                // leak the downscaled buffer; the source bitmap is owned by Coil's cache.
                if (scaled !== bitmap) scaled.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Return a new bitmap whose long edge is at most [maxLongEdgePx] pixels, or
     * [source] unchanged when it already fits.
     */
    private fun downscale(source: Bitmap, maxLongEdgePx: Int): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= maxLongEdgePx) return source
        val scale = maxLongEdgePx.toFloat() / longEdge.toFloat()
        val newWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, /* filter = */ true)
    }

    private companion object {
        /** Long-edge cap that keeps Gemini Vision payloads small while preserving legibility. */
        const val MAX_LONG_EDGE_PX = 1024

        /** JPEG quality factor for the downscaled page sent to Gemini Vision. */
        const val JPEG_QUALITY = 85
    }
}
