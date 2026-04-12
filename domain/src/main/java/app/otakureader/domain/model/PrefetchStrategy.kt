package app.otakureader.domain.model

/**
 * Defines different strategies for smart prefetching of manga pages and chapters.
 *
 * Each strategy balances memory usage, network bandwidth, and reading experience
 * based on different usage scenarios and user preferences.
 */
sealed interface PrefetchStrategy {

    /**
     * How many pages to prefetch before the current page.
     * @param currentPage Current 0-based page index
     * @param totalPages Total number of pages in the chapter
     * @param behavior User's reading behavior profile
     * @return Number of pages to prefetch before current
     */
    fun pagesBefore(currentPage: Int, totalPages: Int, behavior: ReadingBehavior): Int

    /**
     * How many pages to prefetch after the current page.
     * @param currentPage Current 0-based page index
     * @param totalPages Total number of pages in the chapter
     * @param behavior User's reading behavior profile
     * @return Number of pages to prefetch after current
     */
    fun pagesAfter(currentPage: Int, totalPages: Int, behavior: ReadingBehavior): Int

    /**
     * Whether to prefetch the next chapter when approaching chapter end.
     * @param currentPage Current 0-based page index
     * @param totalPages Total number of pages in the chapter
     * @param behavior User's reading behavior profile
     * @return True if next chapter should be prefetched
     */
    fun shouldPrefetchNextChapter(
        currentPage: Int,
        totalPages: Int,
        behavior: ReadingBehavior
    ): Boolean

    /**
     * Whether to prefetch the previous chapter when at chapter start.
     * @param currentPage Current 0-based page index
     * @param behavior User's reading behavior profile
     * @return True if previous chapter should be prefetched
     */
    fun shouldPrefetchPreviousChapter(
        currentPage: Int,
        behavior: ReadingBehavior
    ): Boolean

    /**
     * Conservative prefetching - minimal network/memory usage.
     *
     * Best for:
     * - Limited bandwidth (mobile data)
     * - Low-end devices
     * - Battery preservation
     *
     * Behavior:
     * - Prefetch 1-2 pages ahead
     * - No backward prefetching
     * - No cross-chapter prefetching
     */
    data object Conservative : PrefetchStrategy {
        override fun pagesBefore(currentPage: Int, totalPages: Int, behavior: ReadingBehavior): Int = 0

        override fun pagesAfter(currentPage: Int, totalPages: Int, behavior: ReadingBehavior): Int {
            // Only prefetch 1-2 pages ahead
            return if (currentPage < totalPages - 2) 2 else 1
        }

        override fun shouldPrefetchNextChapter(
            currentPage: Int,
            totalPages: Int,
            behavior: ReadingBehavior
        ): Boolean = false

        override fun shouldPrefetchPreviousChapter(
            currentPage: Int,
            behavior: ReadingBehavior
        ): Boolean = false
    }

    /**
     * Balanced prefetching - good trade-off for most users.
     *
     * Best for:
     * - WiFi connections
     * - Mid-range devices
     * - General use
     *
     * Behavior:
     * - Prefetch 3 pages ahead
     * - Prefetch 1 page behind
     * - Prefetch next chapter when on last 3 pages
     */
    data object Balanced : PrefetchStrategy {
        override fun pagesBefore(currentPage: Int, totalPages: Int, behavior: ReadingBehavior): Int = 1

        override fun pagesAfter(currentPage: Int, totalPages: Int, behavior: ReadingBehavior): Int {
            // Always prefetch 3 pages ahead to match documented "1 before, 3 after" behavior
            return 3
        }

        override fun shouldPrefetchNextChapter(
            currentPage: Int,
            totalPages: Int,
            behavior: ReadingBehavior
        ): Boolean {
            // Prefetch next chapter when on last 3 pages
            return currentPage >= totalPages - 3
        }

        override fun shouldPrefetchPreviousChapter(
            currentPage: Int,
            behavior: ReadingBehavior
        ): Boolean = false
    }

    /**
     * Aggressive prefetching - maximum preloading for seamless experience.
     *
     * Best for:
     * - Fast WiFi connections
     * - High-end devices with ample memory
     * - Downloaded manga (local files)
     *
     * Behavior:
     * - Prefetch 5-10 pages ahead
     * - Prefetch 2-3 pages behind
     * - Prefetch next/previous chapters proactively
     */
    data object Aggressive : PrefetchStrategy {
        override fun pagesBefore(currentPage: Int, totalPages: Int, behavior: ReadingBehavior): Int {
            // More backward prefetching for users who review previous pages
            return if (behavior.forwardNavigationRatio < 0.9f) 3 else 2
        }

        override fun pagesAfter(currentPage: Int, totalPages: Int, behavior: ReadingBehavior): Int {
            // Prefetch up to 10 pages ahead, more near chapter end
            return when {
                currentPage >= totalPages - 5 -> 10 // Near end
                else -> 7
            }
        }

        override fun shouldPrefetchNextChapter(
            currentPage: Int,
            totalPages: Int,
            behavior: ReadingBehavior
        ): Boolean {
            // Prefetch next chapter when on last 5 pages
            return currentPage >= totalPages - 5
        }

        override fun shouldPrefetchPreviousChapter(
            currentPage: Int,
            behavior: ReadingBehavior
        ): Boolean {
            // Prefetch previous chapter if user tends to go backward
            return currentPage <= 2 && behavior.forwardNavigationRatio < 0.85f
        }
    }

    /**
     * Adaptive prefetching - learns from user behavior.
     *
     * Best for:
     * - Users with consistent reading patterns
     * - Variable network conditions
     * - Mixed usage scenarios
     *
     * Behavior:
     * - Adjusts prefetch range based on reading speed
     * - Predicts next actions from historical behavior
     * - Optimizes for detected reading mode (webtoon vs paged)
     */
    data object Adaptive : PrefetchStrategy {
        override fun pagesBefore(currentPage: Int, totalPages: Int, behavior: ReadingBehavior): Int {
            // Adapt based on navigation patterns
            return when {
                behavior.isUnpredictable -> 1 // Fallback to balanced
                behavior.forwardNavigationRatio < 0.85f -> 2 // User reviews pages
                else -> 1
            }
        }

        override fun pagesAfter(currentPage: Int, totalPages: Int, behavior: ReadingBehavior): Int {
            // Adapt based on reading speed and completion rate
            return when {
                behavior.isUnpredictable -> 3 // Fallback to balanced

                // Fast readers (< 3s per page) - prefetch more
                behavior.averagePageDurationMs < 3000L -> {
                    when {
                        currentPage >= totalPages - 3 -> 8
                        else -> 6
                    }
                }

                // Slow readers (> 8s per page) - prefetch less
                behavior.averagePageDurationMs > 8000L -> {
                    when {
                        currentPage >= totalPages - 3 -> 4
                        else -> 2
                    }
                }

                // Normal speed - balanced approach
                else -> {
                    when {
                        currentPage >= totalPages - 3 -> 5
                        else -> 3
                    }
                }
            }
        }

        override fun shouldPrefetchNextChapter(
            currentPage: Int,
            totalPages: Int,
            behavior: ReadingBehavior
        ): Boolean {
            // Only prefetch if user typically completes chapters
            return when {
                !behavior.likelyToCompleteChapter -> false
                behavior.isUnpredictable -> currentPage >= totalPages - 3
                behavior.averagePageDurationMs < 3000L -> currentPage >= totalPages - 5 // Fast reader
                else -> currentPage >= totalPages - 3
            }
        }

        override fun shouldPrefetchPreviousChapter(
            currentPage: Int,
            behavior: ReadingBehavior
        ): Boolean {
            // Only prefetch if user tends to go backward
            return currentPage <= 2 &&
                behavior.forwardNavigationRatio < 0.85f &&
                !behavior.isUnpredictable
        }
    }

    companion object {
        /**
         * Get strategy from ordinal value (for DataStore persistence).
         * 0 = Conservative, 1 = Balanced, 2 = Aggressive, 3 = Adaptive
         */
        fun fromOrdinal(ordinal: Int): PrefetchStrategy = when (ordinal) {
            0 -> Conservative
            1 -> Balanced
            2 -> Aggressive
            3 -> Adaptive
            else -> Balanced // Default fallback
        }

        /**
         * Get ordinal value for strategy (for DataStore persistence).
         */
        fun toOrdinal(strategy: PrefetchStrategy): Int = when (strategy) {
            Conservative -> 0
            Balanced -> 1
            Aggressive -> 2
            Adaptive -> 3
        }
    }
}
