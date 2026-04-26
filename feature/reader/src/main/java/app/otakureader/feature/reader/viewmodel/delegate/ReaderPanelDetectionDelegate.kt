package app.otakureader.feature.reader.viewmodel.delegate

import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.panel.PanelDetectionService
import app.otakureader.feature.reader.viewmodel.ReaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import javax.inject.Inject

class ReaderPanelDetectionDelegate @Inject constructor(
    private val panelDetectionService: PanelDetectionService,
) {
    private var detectionJob: Job? = null

    fun detectForPages(
        scope: CoroutineScope,
        pages: List<ReaderPage>,
        currentPageIndex: Int,
        readingDirection: ReadingDirection,
        isSmartPanelsMode: () -> Boolean,
        updateState: ((ReaderState) -> ReaderState) -> Unit,
    ) {
        detectionJob?.cancel()
        detectionJob = scope.launch {
            val sortedIndices = pages.indices.sortedBy { abs(it - currentPageIndex) }
            for (index in sortedIndices) {
                if (!isSmartPanelsMode()) break
                val page = pages.getOrNull(index) ?: continue
                if (page.panels.isNotEmpty() || page.imageUrl == null) continue

                val detected = panelDetectionService.detectPanelsFromUrl(
                    imageUrl = page.imageUrl,
                    readingDirection = readingDirection,
                )
                if (detected.isNotEmpty()) {
                    updateState { state ->
                        if (index >= state.pages.size) return@updateState state
                        state.copy(
                            pages = state.pages.mapIndexed { i, p ->
                                if (i == index) p.copy(panels = detected) else p
                            }
                        )
                    }
                }
            }
        }
    }

    fun cancel() {
        detectionJob?.cancel()
        detectionJob = null
    }
}
