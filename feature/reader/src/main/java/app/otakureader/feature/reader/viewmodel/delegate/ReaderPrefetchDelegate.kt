package app.otakureader.feature.reader.viewmodel.delegate

import android.content.Context
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.PrefetchStrategy
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.prefetch.AdaptiveChapterPrefetcher
import app.otakureader.feature.reader.prefetch.ReadingBehaviorTracker
import app.otakureader.feature.reader.prefetch.SmartPrefetchManager
import app.otakureader.feature.reader.repository.ReaderSettingsRepository
import coil3.ImageLoader
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

class ReaderPrefetchDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smartPrefetchManager: SmartPrefetchManager,
    private val behaviorTracker: ReadingBehaviorTracker,
    private val chapterPrefetcher: AdaptiveChapterPrefetcher,
    private val imageLoader: ImageLoader,
) {
    private var preloadJob: Job? = null

    var cachedSmartPrefetchEnabled: Boolean = false
    var cachedPrefetchStrategy: PrefetchStrategy = PrefetchStrategy.Balanced
    var cachedAdaptiveLearningEnabled: Boolean = false
    var cachedPrefetchAdjacentChapters: Boolean = false
    var cachedPrefetchOnlyOnWiFi: Boolean = true
    var cachedPreloadBefore: Int = ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES
    var cachedPreloadAfter: Int = ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES

    fun preloadPages(
        scope: CoroutineScope,
        pages: List<ReaderPage>,
        currentPage: Int,
        mangaId: Long,
        chapterId: Long,
        currentManga: Manga?,
    ) {
        preloadJob?.cancel()
        preloadJob = scope.launch {
            if (cachedSmartPrefetchEnabled) {
                val behavior = behaviorTracker.getBehaviorForManga(mangaId)
                smartPrefetchManager.prefetchPages(
                    pages = pages,
                    currentPage = currentPage,
                    strategy = cachedPrefetchStrategy,
                    behavior = behavior,
                    onlyOnWiFi = cachedPrefetchOnlyOnWiFi,
                    scope = scope,
                )
                if (cachedPrefetchAdjacentChapters) {
                    chapterPrefetcher.prefetchAdjacentChapters(
                        currentChapterId = chapterId,
                        mangaId = mangaId,
                        currentPage = currentPage,
                        totalPages = pages.size,
                        strategy = cachedPrefetchStrategy,
                        behavior = behavior,
                        scope = scope,
                        sourceId = currentManga?.sourceId?.toString(),
                    )
                }
            } else {
                val preloadBefore = currentManga?.preloadPagesBefore ?: cachedPreloadBefore
                val preloadAfter = currentManga?.preloadPagesAfter ?: cachedPreloadAfter
                val preloadRange = (currentPage - preloadBefore)..(currentPage + preloadAfter)
                preloadRange.forEach { index ->
                    if (index in pages.indices && index != currentPage) {
                        val imageUrl = pages[index].imageUrl
                        if (!imageUrl.isNullOrBlank()) {
                            try {
                                imageLoader.enqueue(ImageRequest.Builder(context).data(imageUrl).build())
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Record a page view for smart-prefetch telemetry. No-op when smart prefetch
     * is disabled to avoid unnecessary work.
     */
    fun recordPageView(page: ReaderPage) {
        if (!cachedSmartPrefetchEnabled) return
        smartPrefetchManager.recordPageView(page)
    }

    fun cancel() {
        preloadJob?.cancel()
        preloadJob = null
    }

    fun clearCache() {
        smartPrefetchManager.clearCache()
        chapterPrefetcher.clearPrefetchedChapters()
    }
}
