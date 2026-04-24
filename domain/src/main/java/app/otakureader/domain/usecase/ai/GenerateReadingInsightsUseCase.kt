package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.model.InsightCategory
import app.otakureader.domain.model.ReadingInsight
import app.otakureader.domain.model.ReadingInsightsResult
import app.otakureader.domain.model.ReadingStats
import app.otakureader.domain.repository.AiRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
private data class InsightDto(val text: String, val category: String)

@Serializable
private data class InsightsResponseDto(val insights: List<InsightDto>)

class GenerateReadingInsightsUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val aiFeatureGate: AiFeatureGate,
    private val json: Json = Json { ignoreUnknownKeys = true },
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
        val validCategories = InsightCategory.entries.joinToString { it.name }
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

            Respond ONLY with a JSON object matching this exact schema, with no extra text:
            {"insights":[{"text":"<insight under 120 chars>","category":"<one of: $validCategories>"}]}

            Be warm and encouraging. Vary the categories across insights.
        """.trimIndent()
    }

    private fun parseInsights(raw: String): ReadingInsightsResult {
        val jsonStr = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val dto = runCatching { json.decodeFromString<InsightsResponseDto>(jsonStr) }.getOrNull()
        val insights = dto?.insights?.map { insight ->
            val category = runCatching {
                InsightCategory.valueOf(insight.category.uppercase())
            }.getOrDefault(InsightCategory.GENERAL)
            ReadingInsight(text = insight.text, category = category)
        } ?: emptyList()
        return ReadingInsightsResult(insights = insights, generatedAt = System.currentTimeMillis())
    }
}
