package app.otakureader.domain.model

import androidx.compose.runtime.Immutable

/**
 * Telemetry data for tracking prefetch effectiveness and optimization.
 *
 * This model captures metrics to tune the smart prefetch system and
 * provide insights into cache performance.
 */
@Immutable
data class PrefetchTelemetry(
    /** Total number of pages prefetched. */
    val pagesPrefetched: Long = 0L,

    /** Number of prefetched pages that were actually viewed (cache hits). */
    val cacheHits: Long = 0L,

    /** Number of prefetched pages that were never viewed (wasted prefetch). */
    val cacheMisses: Long = 0L,

    /** Number of pages loaded on-demand (not prefetched). */
    val onDemandLoads: Long = 0L,

    /** Total bytes downloaded for prefetching. */
    val bytesDownloaded: Long = 0L,

    /** Number of chapters prefetched. */
    val chaptersPrefetched: Long = 0L,

    /** Number of prefetched chapters that were viewed. */
    val chapterCacheHits: Long = 0L,

    /** Timestamp of last telemetry reset (epoch millis). */
    val lastResetAt: Long = System.currentTimeMillis()
) {
    /**
     * Cache hit rate as a percentage (0.0 to 1.0).
     * Higher is better - indicates prefetch predictions are accurate.
     */
    val hitRate: Float
        get() = if (cacheHits + onDemandLoads > 0) {
            cacheHits.toFloat() / (cacheHits + onDemandLoads).toFloat()
        } else {
            0f
        }

    /**
     * Prefetch efficiency rate (0.0 to 1.0).
     * Ratio of prefetched pages that were actually used.
     * Higher is better - indicates minimal wasted bandwidth.
     */
    val efficiency: Float
        get() = if (pagesPrefetched > 0) {
            (cacheHits.toFloat() / pagesPrefetched.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    /**
     * Average bytes per page.
     */
    val averageBytesPerPage: Long
        get() = if (pagesPrefetched > 0) bytesDownloaded / pagesPrefetched else 0L

    /**
     * True if this telemetry profile has enough data to be meaningful.
     */
    val hasSufficientData: Boolean
        get() = pagesPrefetched >= 100

    companion object {
        /** Empty telemetry profile. */
        val EMPTY = PrefetchTelemetry()
    }
}
