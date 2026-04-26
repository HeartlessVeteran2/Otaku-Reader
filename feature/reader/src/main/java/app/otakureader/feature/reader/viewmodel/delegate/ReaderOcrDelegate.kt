package app.otakureader.feature.reader.viewmodel.delegate

import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.ocr.TextRecognitionService
import app.otakureader.feature.reader.viewmodel.ReaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Delegate that manages lazy, per-page OCR jobs for the text-search feature.
 *
 * Strategy:
 * - When the user opens the OCR search sheet, we immediately OCR the current page
 *   and then continue scanning remaining pages in the background.
 * - Each page is processed at most once per session (results are cached by
 *   [TextRecognitionService]).
 * - As pages are indexed, the state is updated so the UI shows live results.
 */
class ReaderOcrDelegate @Inject constructor(
    private val textRecognitionService: TextRecognitionService,
) {
    private val pageJobs = mutableMapOf<Int, Job>()
    private var batchJob: Job? = null

    /**
     * OCR a single page on-demand (e.g. when the user just scrolled to it).
     * No-ops if a job is already running for that page.
     */
    fun recognizePage(
        scope: CoroutineScope,
        pageIndex: Int,
        page: ReaderPage,
        updateState: ((ReaderState) -> ReaderState) -> Unit,
    ) {
        if (page.imageUrl == null) return
        if (pageJobs[pageIndex]?.isActive == true) return

        pageJobs[pageIndex] = scope.launch {
            val text = textRecognitionService.recognizeText(page.imageUrl)
            updateState { state ->
                state.copy(
                    ocrPageTexts = state.ocrPageTexts + (pageIndex to text),
                )
            }
        }.also { job -> job.invokeOnCompletion { pageJobs.remove(pageIndex) } }
    }

    /**
     * Start a background batch job that OCRs all pages not yet indexed,
     * prioritizing pages closest to [currentPageIndex].
     *
     * Only one batch job runs at a time; calling this again cancels the previous one.
     */
    fun startBatchOcr(
        scope: CoroutineScope,
        pages: List<ReaderPage>,
        currentPageIndex: Int,
        updateState: ((ReaderState) -> ReaderState) -> Unit,
    ) {
        batchJob?.cancel()
        batchJob = scope.launch {
            updateState { it.copy(isOcrRunning = true) }

            // Process pages closest to the current page first so results appear quickly.
            val sortedIndices = pages.indices
                .sortedBy { kotlin.math.abs(it - currentPageIndex) }

            for (index in sortedIndices) {
                val page = pages.getOrNull(index) ?: continue
                if (page.imageUrl == null) continue

                val text = textRecognitionService.recognizeText(page.imageUrl)
                updateState { state ->
                    state.copy(
                        ocrPageTexts = state.ocrPageTexts + (index to text),
                    )
                }
            }

            updateState { it.copy(isOcrRunning = false) }
        }
    }

    /**
     * Cancel all running OCR jobs (e.g. when the search sheet is closed).
     * Cached results in [TextRecognitionService] are preserved for the session.
     */
    fun cancelAll() {
        batchJob?.cancel()
        batchJob = null
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
    }
}
