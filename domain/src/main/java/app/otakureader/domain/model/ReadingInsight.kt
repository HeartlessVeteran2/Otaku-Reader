package app.otakureader.domain.model

import androidx.compose.runtime.Immutable

/** A single AI-generated insight about the user's reading habits. */
@Immutable
data class ReadingInsight(
    val text: String,
    val category: InsightCategory = InsightCategory.GENERAL
)

enum class InsightCategory {
    GENERAL, STREAK, GENRE, PACE, GOAL
}

/** Wrapper holding a list of insights and when they were generated. */
@Immutable
data class ReadingInsightsResult(
    val insights: List<ReadingInsight>,
    val generatedAt: Long = System.currentTimeMillis()
)
