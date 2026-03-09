package app.otakureader.domain.model

/** Aggregated reading statistics for the Statistics screen. */
data class ReadingStats(
    val totalMangaInLibrary: Int = 0,
    val totalChaptersRead: Int = 0,
    val totalReadingTimeMs: Long = 0L,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val genreDistribution: Map<String, Int> = emptyMap(),
    val readingActivityByDay: Map<String, Int> = emptyMap()
)
