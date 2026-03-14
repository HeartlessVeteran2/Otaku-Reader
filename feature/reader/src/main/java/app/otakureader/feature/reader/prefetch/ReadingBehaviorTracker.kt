package app.otakureader.feature.reader.prefetch

import app.otakureader.domain.model.PageNavigationEvent
import app.otakureader.domain.model.ReadingBehavior
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks and analyzes user reading behavior to optimize prefetching.
 *
 * This class maintains a rolling window of navigation events and computes
 * aggregate statistics to predict likely reading patterns.
 */
@Singleton
class ReadingBehaviorTracker @Inject constructor() {

    // Rolling window of recent navigation events (max 500 events)
    private val navigationHistory = ArrayDeque<PageNavigationEvent>(MAX_HISTORY_SIZE)

    // Current behavior profile (updated incrementally)
    @Volatile
    private var _currentBehavior = ReadingBehavior.DEFAULT
    val currentBehavior: ReadingBehavior get() = _currentBehavior

    /**
     * Records a page navigation event and updates behavior profile.
     *
     * @param event The navigation event to record
     */
    fun recordNavigation(event: PageNavigationEvent) {
        synchronized(navigationHistory) {
            // Add to rolling window
            navigationHistory.addLast(event)

            // Maintain max size
            if (navigationHistory.size > MAX_HISTORY_SIZE) {
                navigationHistory.removeFirst()
            }

            // Update behavior profile if we have enough data
            if (navigationHistory.size >= ReadingBehavior.MIN_SAMPLE_SIZE) {
                updateBehaviorProfile()
            }
        }
    }

    /**
     * Computes behavior profile from navigation history.
     */
    private fun updateBehaviorProfile() {
        if (navigationHistory.isEmpty()) {
            _currentBehavior = ReadingBehavior.DEFAULT
            return
        }

        val totalEvents = navigationHistory.size
        val forwardEvents = navigationHistory.count { it.isForward }
        val sequentialEvents = navigationHistory.count { it.isSequential }

        // Calculate forward navigation ratio
        val forwardRatio = forwardEvents.toFloat() / totalEvents.toFloat()

        // Calculate sequential navigation ratio
        val sequentialRatio = sequentialEvents.toFloat() / totalEvents.toFloat()

        // Calculate average page duration (exclude outliers > 60s)
        val pageDurations = navigationHistory
            .map { it.pageDurationMs }
            .filter { it in 100L..60_000L } // Exclude < 100ms (accidental) and > 60s (idle)

        val avgPageDuration = if (pageDurations.isNotEmpty()) {
            pageDurations.average().toLong()
        } else {
            5000L // Default 5 seconds
        }

        // Calculate completion rate (ratio of chapters read to the end)
        val completionRate = calculateCompletionRate()

        // Determine preferred reader mode (most common)
        val preferredMode = navigationHistory
            .groupingBy { it.readerMode }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: 0

        _currentBehavior = ReadingBehavior(
            forwardNavigationRatio = forwardRatio,
            averagePageDurationMs = avgPageDuration,
            completionRate = completionRate,
            sequentialNavigationRatio = sequentialRatio,
            preferredReaderMode = preferredMode,
            sampleSize = totalEvents
        )
    }

    /**
     * Calculates the completion rate (chapters read to the end).
     *
     * Groups navigation events by chapter and checks if the chapter
     * was read to near the end (within last 3 pages).
     */
    private fun calculateCompletionRate(): Float {
        val chapterSessions = navigationHistory.groupBy { it.chapterId }

        if (chapterSessions.isEmpty()) return 0.8f // Default

        var completedChapters = 0

        chapterSessions.forEach { (_, events) ->
            // Sort by timestamp to get chronological order
            val sortedEvents = events.sortedBy { it.timestamp }

            // Check if last event is near the end
            val lastEvent = sortedEvents.lastOrNull()
            if (lastEvent != null) {
                // Consider completed if reached within last 3 pages
                // We don't have total pages here, so we use a heuristic:
                // if the highest page reached is significantly higher than average
                val maxPage = events.maxOfOrNull { maxOf(it.fromPage, it.toPage) } ?: 0
                val minPage = events.minOfOrNull { minOf(it.fromPage, it.toPage) } ?: 0
                val lastVisitedPage = lastEvent.toPage

                // Consider completed when the reader finishes near the furthest page reached
                // and has meaningfully progressed through the chapter.
                if ((maxPage - minPage) >= 3 && lastVisitedPage >= (maxPage - 3)) {
                    completedChapters++
                }
            }
        }

        return completedChapters.toFloat() / chapterSessions.size.toFloat()
    }

    /**
     * Returns behavior profile for a specific manga (if available).
     *
     * This allows per-manga behavior tracking in the future.
     * Currently returns global behavior.
     */
    fun getBehaviorForManga(mangaId: Long): ReadingBehavior {
        // For now, return global behavior
        // In the future, we could track per-manga behavior
        return _currentBehavior
    }

    /**
     * Clears navigation history and resets to default behavior.
     */
    fun reset() {
        synchronized(navigationHistory) {
            navigationHistory.clear()
            _currentBehavior = ReadingBehavior.DEFAULT
        }
    }

    /**
     * Returns the number of navigation events recorded.
     */
    fun getSampleSize(): Int = synchronized(navigationHistory) {
        navigationHistory.size
    }

    companion object {
        /** Maximum number of navigation events to keep in history. */
        private const val MAX_HISTORY_SIZE = 500
    }
}
