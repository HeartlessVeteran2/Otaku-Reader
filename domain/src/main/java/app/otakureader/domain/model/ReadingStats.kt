package app.otakureader.domain.model

import androidx.compose.runtime.Immutable

/** Aggregated reading statistics for the Statistics screen. */
@Immutable
data class ReadingStats(
    val totalMangaInLibrary: Int = 0,
    val totalChaptersRead: Int = 0,
    val totalReadingTimeMs: Long = 0L,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val genreDistribution: Map<String, Int> = emptyMap(),
    val readingActivityByDay: Map<String, Int> = emptyMap(),
    val completedMangaCount: Int = 0,
    val totalChapterCount: Int = 0,
    /** Distinct manga with at least one tracker entry. */
    val trackedMangaCount: Int = 0,
    /** Mean of non-zero tracker scores (normalized 0–10), or null when nothing is scored. */
    val meanTrackerScore: Float? = null,
    /** Distinct tracker services in use. */
    val trackerServiceCount: Int = 0,
)
