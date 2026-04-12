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
    val readingActivityByDay: Map<String, Int> = emptyMap()
)
