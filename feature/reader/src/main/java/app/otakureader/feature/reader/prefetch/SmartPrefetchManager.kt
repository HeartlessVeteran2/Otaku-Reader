package app.otakureader.feature.reader.prefetch

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.otakureader.domain.model.PrefetchStrategy
import app.otakureader.domain.model.ReadingBehavior
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
 * Manages smart prefetching of manga pages based on user behavior and strategy.
 *
 * This class:
 * - Decides which pages to prefetch based on strategy and behavior
 * - Respects network conditions (WiFi vs mobile data)
 * - Tracks telemetry for optimization
 * - Coordinates with Coil's ImageLoader for actual prefetching
 */
@Singleton
class SmartPrefetchManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val behaviorTracker: ReadingBehaviorTracker
) {

    // Current prefetch job
    private var prefetchJob: Job? = null

    // Pages that have been prefetched (URL -> timestamp)
    private val prefetchedPages = mutableMapOf<String, Long>()

    // Pages that were viewed (for telemetry)
    private val viewedPages = mutableSetOf<String>()

    // Telemetry counters
    private var pagesPrefetched = 0L
    private var cacheHits = 0L
    private var onDemandLoads = 0L

    /**
     * Prefetches pages according to the given strategy and behavior.
     *
     * @param pages All pages in the current chapter
     * @param currentPage Current 0-based page index
     * @param strategy Prefetch strategy to use
     * @param behavior User reading behavior profile
     * @param onlyOnWiFi If true, skip prefetching on mobile data
     * @param scope Coroutine scope for launching prefetch jobs
     */
    fun prefetchPages(
        pages: List<ReaderPage>,
        currentPage: Int,
        strategy: PrefetchStrategy,
        behavior: ReadingBehavior,
        onlyOnWiFi: Boolean,
        scope: CoroutineScope
    ) {
        // Cancel previous prefetch job
        prefetchJob?.cancel()

        // Check network conditions
        if (onlyOnWiFi && !isOnWiFi()) {
            return
        }

        prefetchJob = scope.launch {
            val totalPages = pages.size

            // Determine prefetch ranges based on strategy
            val pagesBefore = strategy.pagesBefore(currentPage, totalPages, behavior)
            val pagesAfter = strategy.pagesAfter(currentPage, totalPages, behavior)

            val prefetchRange = (currentPage - pagesBefore)..(currentPage + pagesAfter)

            // Prefetch pages in range
            prefetchRange.forEach { index ->
                if (index in pages.indices && index != currentPage) {
                    prefetchPage(pages[index])
                }
            }
        }
    }

    /**
     * Prefetches a single page using Coil's prefetch API.
     *
     * @param page The page to prefetch
     */
    private fun prefetchPage(page: ReaderPage) {
        val imageUrl = page.imageUrl
        if (imageUrl.isNullOrBlank()) return

        val now = System.currentTimeMillis()

        // Decide and record prefetch under lock to avoid races.
        val shouldPrefetch = synchronized(prefetchedPages) {
            prefetchedPages.entries.removeAll { now - it.value >= 300_000L }

            val lastPrefetchTime = prefetchedPages[imageUrl]
            if (lastPrefetchTime != null && now - lastPrefetchTime < 300_000L) {
                false
            } else {
                prefetchedPages[imageUrl] = now
                pagesPrefetched++
                true
            }
        }

        if (!shouldPrefetch) return

        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .build()

            // Enqueue prefetch request (non-blocking)
            imageLoader.enqueue(request)
        } catch (e: Exception) {
            // Silently ignore prefetch failures - they're not critical
        }
    }

    /**
     * Records that a page was viewed (for telemetry).
     *
     * @param page The page that was viewed
     */
    fun recordPageView(page: ReaderPage) {
        val imageUrl = page.imageUrl ?: return

        synchronized(prefetchedPages) {
            // Check if this was a cache hit (prefetched) or on-demand load
            if (prefetchedPages.containsKey(imageUrl)) {
                cacheHits++
            } else {
                onDemandLoads++
            }

            viewedPages.add(imageUrl)
        }
    }

    /**
     * Returns cache hit rate (0.0 to 1.0).
     */
    fun getCacheHitRate(): Float {
        return synchronized(prefetchedPages) {
            val totalViews = cacheHits + onDemandLoads
            if (totalViews > 0) {
                cacheHits.toFloat() / totalViews.toFloat()
            } else {
                0f
            }
        }
    }

    /**
     * Returns prefetch efficiency (0.0 to 1.0).
     * Ratio of prefetched pages that were actually viewed.
     */
    fun getPrefetchEfficiency(): Float {
        return synchronized(prefetchedPages) {
            if (pagesPrefetched > 0) {
                val viewedPrefetched = viewedPages.count { prefetchedPages.containsKey(it) }
                viewedPrefetched.toFloat() / pagesPrefetched.toFloat()
            } else {
                0f
            }
        }
    }

    /**
     * Resets telemetry counters.
     */
    fun resetTelemetry() {
        pagesPrefetched = 0L
        cacheHits = 0L
        onDemandLoads = 0L
        prefetchedPages.clear()
        viewedPages.clear()
    }

    /**
     * Cancels any active prefetch job.
     */
    fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    /**
     * Checks if the device is connected to WiFi.
     *
     * @return True if connected to WiFi, false otherwise
     */
    private fun isOnWiFi(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Returns telemetry stats as a formatted string.
     */
    fun getTelemetryStats(): String {
        return buildString {
            appendLine("Smart Prefetch Telemetry:")
            appendLine("  Pages Prefetched: $pagesPrefetched")
            appendLine("  Cache Hits: $cacheHits")
            appendLine("  On-Demand Loads: $onDemandLoads")
            appendLine("  Hit Rate: ${"%.1f".format(getCacheHitRate() * 100)}%")
            appendLine("  Efficiency: ${"%.1f".format(getPrefetchEfficiency() * 100)}%")
        }
    }
}
