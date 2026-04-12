package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.model.SourceScore
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.SourceIntelligenceRepository
import javax.inject.Inject

/**
 * Input model describing a single source candidate for evaluation.
 *
 * @property sourceId Unique identifier of the source.
 * @property sourceName Human-readable display name of the source.
 * @property chapterCount Number of chapters the source has for the manga.
 * @property latestChapterName Name of the most recently updated chapter, or `null`.
 * @property language Primary language of the source.
 */
data class SourceInfo(
    val sourceId: String,
    val sourceName: String,
    val chapterCount: Int,
    val latestChapterName: String? = null,
    val language: String = "en",
)

/**
 * Ranks available sources for a manga using AI and caches the result.
 *
 * The use case:
 * 1. Checks the [AiFeatureGate] for [AiFeature.SOURCE_INTELLIGENCE].
 * 2. Returns the cached ranking from [SourceIntelligenceRepository] when available.
 * 3. Otherwise, sends a prompt with the source metadata to the AI which returns
 *    scores for content quality, update frequency, and reliability.
 * 4. Persists and returns the scored list sorted by overall score descending.
 *
 * **Graceful degradation**: When AI is unavailable the use case returns an
 * empty-list success so callers can fall back to the default (unsorted) source list.
 */
class ScoreSourcesForMangaUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val aiFeatureGate: AiFeatureGate,
    private val sourceIntelligenceRepository: SourceIntelligenceRepository,
) {

    /**
     * Score and rank sources for the specified manga.
     *
     * @param mangaId Database ID of the manga.
     * @param mangaTitle Title of the manga (used in the AI prompt).
     * @param sources Candidate sources to evaluate.
     * @return [Result] containing the list of [SourceScore]s sorted by overall score
     *   descending. Returns an empty-list success when AI is unavailable.
     */
    suspend operator fun invoke(
        mangaId: Long,
        mangaTitle: String,
        sources: List<SourceInfo>,
    ): Result<List<SourceScore>> {
        if (!aiFeatureGate.isFeatureAvailable(AiFeature.SOURCE_INTELLIGENCE)) {
            return Result.success(emptyList())
        }

        if (sources.isEmpty()) {
            return Result.success(emptyList())
        }

        // Return cached scores if available
        val cached = sourceIntelligenceRepository.getScores(mangaId)
        if (cached.isNotEmpty()) {
            return Result.success(cached)
        }

        val prompt = buildPrompt(mangaTitle, sources)
        val aiResult = aiRepository.generateContent(prompt)

        if (aiResult.isFailure) {
            return Result.success(emptyList())
        }

        val scores = parseAiResponse(aiResult.getOrNull().orEmpty(), mangaId, sources)
            .sortedByDescending { it.overallScore }

        sourceIntelligenceRepository.saveScores(mangaId, scores)
        return Result.success(scores)
    }

    private fun buildPrompt(mangaTitle: String, sources: List<SourceInfo>): String = buildString {
        appendLine("You are a manga source quality analyst. Evaluate the following sources for \"$mangaTitle\".")
        appendLine()
        appendLine("Sources:")
        sources.forEachIndexed { index, source ->
            appendLine("${index + 1}. ID=${source.sourceId} Name=${source.sourceName}")
            appendLine("   Chapters=${source.chapterCount} Language=${source.language}")
            if (source.latestChapterName != null) {
                appendLine("   Latest=${source.latestChapterName}")
            }
        }
        appendLine()
        appendLine("For each source, respond with scores from 0.00 to 1.00 and a brief recommendation.")
        appendLine("Use this exact pipe-separated format (one line per source):")
        appendLine("SOURCE_ID|QUALITY|FREQUENCY|RELIABILITY|RECOMMENDATION")
        appendLine()
        appendLine("Where:")
        appendLine("  QUALITY      = translation/scan quality estimate")
        appendLine("  FREQUENCY    = estimated update frequency")
        appendLine("  RELIABILITY  = estimated uptime/availability")
        appendLine("  RECOMMENDATION = one sentence explaining the ranking")
    }

    /**
     * Parse the structured AI response into [SourceScore] objects.
     *
     * Falls back to a default score of 0.5 for any source that the AI omits or
     * whose response line cannot be parsed.
     */
    internal fun parseAiResponse(
        response: String,
        mangaId: Long,
        sources: List<SourceInfo>,
    ): List<SourceScore> {
        val parsedById = mutableMapOf<String, SourceScore>()

        response.trim().lines().forEach { line ->
            // Split with a limit so pipes inside the recommendation text are preserved.
            val parts = line.split("|", limit = RESPONSE_PARTS_COUNT)
            if (parts.size < RESPONSE_PARTS_COUNT) return@forEach
            val sourceId = parts[SOURCE_ID_INDEX].trim()
            val quality = parts[QUALITY_INDEX].trim().toFloatOrNull() ?: return@forEach
            val frequency = parts[FREQUENCY_INDEX].trim().toFloatOrNull() ?: return@forEach
            val reliability = parts[RELIABILITY_INDEX].trim().toFloatOrNull() ?: return@forEach
            val recommendation = parts[RECOMMENDATION_INDEX].trim()

            val overall = (quality * QUALITY_WEIGHT + frequency * FREQUENCY_WEIGHT + reliability * RELIABILITY_WEIGHT)
                .coerceIn(0f, 1f)

            parsedById[sourceId] = SourceScore(
                sourceId = sourceId,
                mangaId = mangaId,
                contentQualityScore = quality.coerceIn(0f, 1f),
                updateFrequencyScore = frequency.coerceIn(0f, 1f),
                reliabilityScore = reliability.coerceIn(0f, 1f),
                overallScore = overall,
                recommendation = recommendation,
            )
        }

        // Ensure every requested source has a score (use defaults for unparsed ones)
        return sources.map { source ->
            parsedById[source.sourceId] ?: SourceScore(
                sourceId = source.sourceId,
                mangaId = mangaId,
                contentQualityScore = DEFAULT_SCORE,
                updateFrequencyScore = DEFAULT_SCORE,
                reliabilityScore = DEFAULT_SCORE,
                overallScore = DEFAULT_SCORE,
                recommendation = "No AI analysis available.",
            )
        }
    }

    companion object {
        private const val RESPONSE_PARTS_COUNT = 5
        private const val SOURCE_ID_INDEX = 0
        private const val QUALITY_INDEX = 1
        private const val FREQUENCY_INDEX = 2
        private const val RELIABILITY_INDEX = 3
        private const val RECOMMENDATION_INDEX = 4

        private const val QUALITY_WEIGHT = 0.4f
        private const val FREQUENCY_WEIGHT = 0.3f
        private const val RELIABILITY_WEIGHT = 0.3f
        private const val DEFAULT_SCORE = 0.5f
    }
}
