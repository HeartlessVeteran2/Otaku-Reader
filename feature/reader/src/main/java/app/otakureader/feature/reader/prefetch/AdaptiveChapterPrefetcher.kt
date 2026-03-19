package app.otakureader.feature.reader.prefetch

import android.content.Context
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.PrefetchStrategy
import app.otakureader.domain.model.ReadingBehavior
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.feature.reader.model.ReaderPage
import coil3.ImageLoader
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages cross-chapter prefetching for seamless reading experience.
 *
 * This class:
 * - Prefetches first few pages of next/previous chapters
 * - Respects user behavior and prefetch strategy
 * - Avoids redundant prefetching
 */
@Singleton
class AdaptiveChapterPrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val chapterRepository: ChapterRepository,
    private val sourceRepository: app.otakureader.domain.repository.SourceRepository
) {





























    // Current prefetch jobs
    private var nextChapterPrefetchJob: Job? = null
    private var previousChapterPrefetchJob: Job? = null

    // Chapters that have been prefetched
    private val prefetchedChapters: MutableSet<Long> = mutableSetOf()

    /**
     * Attempts to prefetch adjacent chapters based on strategy and behavior.
     *
     * @param currentChapterId Current chapter ID
     * @param mangaId Manga ID
     * @param currentPage Current page index
     * @param totalPages Total pages in current chapter
     * @param strategy Prefetch strategy
     * @param behavior User reading behavior
     * @param scope Coroutine scope for launching prefetch jobs
     * @param sourceId Source ID string for the manga (used to fetch page URLs)
     */
    fun prefetchAdjacentChapters(
        currentChapterId: Long,
        mangaId: Long,
        currentPage: Int,
        totalPages: Int,
        strategy: PrefetchStrategy,
        behavior: ReadingBehavior,
        scope: CoroutineScope,
        sourceId: String? = null
    ) {
        // Check if we should prefetch next chapter
        if (strategy.shouldPrefetchNextChapter(currentPage, totalPages, behavior)) {
            prefetchNextChapter(currentChapterId, mangaId, scope, sourceId)
        }

        // Check if we should prefetch previous chapter
        if (strategy.shouldPrefetchPreviousChapter(currentPage, behavior)) {
            prefetchPreviousChapter(currentChapterId, mangaId, scope, sourceId)
        }
    }

    /**
     * Prefetches the first few pages of the next chapter.
     */
    private fun prefetchNextChapter(currentChapterId: Long, mangaId: Long, scope: CoroutineScope, sourceId: String?) {
        nextChapterPrefetchJob?.cancel()

        if (sourceId == null) return

        nextChapterPrefetchJob = scope.launch {
            try {
                // Get all chapters for this manga
                val chapters = chapterRepository.getChaptersByMangaIdSync(mangaId)

                // Find current chapter index
                val currentIndex = chapters.indexOfFirst { it.id == currentChapterId }
                if (currentIndex == -1 || currentIndex >= chapters.size - 1) {
                    return@launch // No next chapter
                }

                // Get next chapter
                val nextChapter = chapters[currentIndex + 1]

                // Skip if already prefetched
                if (prefetchedChapters.contains(nextChapter.id)) {
                    return@launch
                }

                // Prefetch first N pages of next chapter
                prefetchChapterPages(nextChapter, PREFETCH_PAGES_PER_CHAPTER, sourceId = sourceId)

                // Mark as prefetched
                prefetchedChapters.add(nextChapter.id)
            } catch (e: Exception) {
                // Silently ignore - cross-chapter prefetch is optional
            }
        }
    }

    /**
     * Prefetches the last few pages of the previous chapter.
     */
    private fun prefetchPreviousChapter(currentChapterId: Long, mangaId: Long, scope: CoroutineScope, sourceId: String?) {
        previousChapterPrefetchJob?.cancel()

        if (sourceId == null) return

        previousChapterPrefetchJob = scope.launch {
            try {
                // Get all chapters for this manga
                val chapters = chapterRepository.getChaptersByMangaIdSync(mangaId)

                // Find current chapter index
                val currentIndex = chapters.indexOfFirst { it.id == currentChapterId }
                if (currentIndex <= 0) {
                    return@launch // No previous chapter
                }

                // Get previous chapter
                val previousChapter = chapters[currentIndex - 1]

                // Skip if already prefetched
                if (prefetchedChapters.contains(previousChapter.id)) {
                    return@launch
                }

                // Prefetch last N pages of previous chapter
                prefetchChapterPages(previousChapter, PREFETCH_PAGES_PER_CHAPTER, fromEnd = true, sourceId = sourceId)

                // Mark as prefetched
                prefetchedChapters.add(previousChapter.id)
            } catch (e: Exception) {
                // Silently ignore - cross-chapter prefetch is optional
            }
        }
    }

    /**
     * Prefetches a specified number of pages from a chapter.
     *
     * @param chapter The chapter to prefetch
     * @param pageCount Number of pages to prefetch
     * @param fromEnd If true, prefetch from the end instead of the beginning
     */
    private suspend fun prefetchChapterPages(
        chapter: Chapter,
        pageCount: Int,
        fromEnd: Boolean = false,
        sourceId: String
    ) {
        val sourceChapter = app.otakureader.sourceapi.SourceChapter(
            url = chapter.url,
            name = chapter.name
        )

        val pages = sourceRepository.getPageList(sourceId, sourceChapter).getOrNull()
        if (pages.isNullOrEmpty()) return

        val pagesToPrefetch = if (fromEnd) {
            pages.takeLast(pageCount)
        } else {
            pages.take(pageCount)
        }

        pagesToPrefetch.forEach { page ->
            val url = page.imageUrl ?: return@forEach
            val request = ImageRequest.Builder(context)
                .data(url)
                .build()
            imageLoader.enqueue(request)
        }
    }

    /**
     * Prefetches pages for a list of ReaderPages (when pages are already available).
     *
     * This is used when we already have page URLs (e.g., from downloaded chapters).
     */
    fun prefetchPages(pages: List<ReaderPage>, pageCount: Int, fromEnd: Boolean = false) {
        val pagesToPrefetch = if (fromEnd) {
            pages.takeLast(pageCount)
        } else {
            pages.take(pageCount)
        }

        pagesToPrefetch.forEach { page ->
            val imageUrl = page.imageUrl
            if (!imageUrl.isNullOrBlank()) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .build()
                    imageLoader.enqueue(request)
                } catch (e: Exception) {
                    // Silently ignore prefetch failures
                }
            }
        }
    }

    /**
     * Cancels all active chapter prefetch jobs.
     */
    fun cancelPrefetch() {
        nextChapterPrefetchJob?.cancel()
        previousChapterPrefetchJob?.cancel()
        nextChapterPrefetchJob = null
        previousChapterPrefetchJob = null
    }

    /**
     * Clears the set of prefetched chapters.
     */
    fun clearPrefetchedChapters() {
        prefetchedChapters.clear()
    }

    companion object {
        /** Number of pages to prefetch per chapter. */
        private const val PREFETCH_PAGES_PER_CHAPTER = 5
    }
}
