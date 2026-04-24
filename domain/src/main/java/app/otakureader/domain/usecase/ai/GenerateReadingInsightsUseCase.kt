package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.model.InsightCategory
import app.otakureader.domain.model.ReadingInsight
import app.otakureader.domain.model.ReadingInsightsResult
import app.otakureader.domain.model.ReadingStats
import app.otakureader.domain.repository.AiRepository
import javax.inject.Inject

class GenerateReadingInsightsUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val aiFeatureGate: AiFeatureGate
) {
    suspend operator fun invoke(stats: ReadingStats): Result<ReadingInsightsResult> {
        if (!aiFeatureGate.isFeatureAvailable(AiFeature.READING_INSIGHTS)) {
            return Result.failure(IllegalStateException("Reading insights feature is not available."))
        }

        val prompt = buildPrompt(stats)
        return aiRepository.generateContent(prompt).map { raw ->
            parseInsights(raw)
        }
    }

    private fun buildPrompt(stats: ReadingStats): String {
        val topGenres = stats.genreDistribution
            .entries.sortedByDescending { it.value }
            .take(3).joinToString { it.key }
        val hours = stats.totalReadingTimeMs / 3_600_000
        return """
            You are a reading coach for a manga reader app. Based on the following stats, generate
            3 to 5 concise, friendly, personalized insights about the user's reading habits.

            Stats:
            - Total manga in library: ${stats.totalMangaInLibrary}
            - Total chapters read: ${stats.totalChaptersRead}
            - Total reading time: ~$hours hours
            - Current streak: ${stats.currentStreak} days
            - Best streak: ${stats.bestStreak} days
            - Top genres: $topGenres

            Format: one insight per line, no bullet points, no numbering. Each insight should be
            under 120 characters. Be warm and encouraging. Vary the topics.
        """.trimIndent()
    }

    private fun parseInsights(raw: String): ReadingInsightsResult {
        val insights = raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val category = when {
                    line.contains("streak", ignoreCase = true) -> InsightCategory.STREAK
                    line.contains("genre", ignoreCase = true) || line.contains("action", ignoreCase = true)
                        || line.contains("romance", ignoreCase = true) -> InsightCategory.GENRE
                    line.contains("hour", ignoreCase = true) || line.contains("time", ignoreCase = true)
                        || line.contains("chapter", ignoreCase = true) -> InsightCategory.PACE
                    line.contains("goal", ignoreCase = true) -> InsightCategory.GOAL
                    else -> InsightCategory.GENERAL
                }
                ReadingInsight(text = line, category = category)
            }
        return ReadingInsightsResult(insights = insights)
    }
}
