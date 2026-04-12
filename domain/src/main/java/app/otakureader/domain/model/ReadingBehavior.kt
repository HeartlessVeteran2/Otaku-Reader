package app.otakureader.domain.model

import androidx.compose.runtime.Immutable

/**
 * Represents a user's reading behavior pattern for adaptive prefetching.
 *
 * This model captures navigation patterns to predict likely next actions
 * and optimize page/chapter preloading.
 */
@Immutable
data class ReadingBehavior(
    /** Ratio of forward page navigations (0.0 to 1.0). */
    val forwardNavigationRatio: Float = 0.9f,

    /** Average time spent per page in milliseconds. */
    val averagePageDurationMs: Long = 5000L,

    /** Whether user typically reads to chapter end (vs. sampling/skipping). */
    val completionRate: Float = 0.8f,

    /** Ratio of sequential navigation vs. jumping (0.0 to 1.0). */
    val sequentialNavigationRatio: Float = 0.95f,

    /** Most common reader mode (0=single, 1=dual, 2=webtoon, 3=smart panels). */
    val preferredReaderMode: Int = 0,

    /** Number of page navigation events recorded for this profile. */
    val sampleSize: Int = 0
) {
    /**
     * Returns true if this behavior profile is primarily forward-reading.
     * Used to determine if aggressive forward prefetching is beneficial.
     */
    val isPrimaryForwardReader: Boolean
        get() = forwardNavigationRatio >= 0.85f && sequentialNavigationRatio >= 0.8f

    /**
     * Returns true if user typically completes chapters.
     * Used to decide if cross-chapter prefetching is worthwhile.
     */
    val likelyToCompleteChapter: Boolean
        get() = completionRate >= 0.7f

    /**
     * Returns true if navigation is unpredictable.
     * Used to disable adaptive prefetching in favor of balanced static approach.
     */
    val isUnpredictable: Boolean
        get() = sequentialNavigationRatio < 0.6f || sampleSize < MIN_SAMPLE_SIZE

    companion object {
        /** Minimum sample size required for meaningful statistics. */
        const val MIN_SAMPLE_SIZE = 50

        /** Default behavior profile for new users (assume typical manga reader). */
        val DEFAULT = ReadingBehavior()
    }
}

/**
 * Represents a single page navigation event for behavior tracking.
 */
@Immutable
data class PageNavigationEvent(
    /** Manga ID for this navigation event. */
    val mangaId: Long,

    /** Chapter ID for this navigation event. */
    val chapterId: Long,

    /** Previous page index (0-based). */
    val fromPage: Int,

    /** New page index (0-based). */
    val toPage: Int,

    /** Time spent on the previous page in milliseconds. */
    val pageDurationMs: Long,

    /** Reader mode at time of navigation (0=single, 1=dual, 2=webtoon, 3=smart panels). */
    val readerMode: Int,

    /** Timestamp when navigation occurred (epoch millis). */
    val timestamp: Long = System.currentTimeMillis()
) {
    /** True if this is a forward navigation (next page). */
    val isForward: Boolean
        get() = toPage > fromPage

    /** True if this is a sequential navigation (adjacent page). */
    val isSequential: Boolean
        get() = when (readerMode) {
            1 -> kotlin.math.abs(toPage - fromPage) == 2 // DUAL_PAGE mode advances by 2
            else -> kotlin.math.abs(toPage - fromPage) == 1
        }
}
