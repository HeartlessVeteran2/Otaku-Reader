package app.otakureader.domain.usecase.ai

import app.otakureader.domain.repository.AiRepository
import javax.inject.Inject

/**
 * Use case for analyzing a manga source using AI to produce quality indicators.
 *
 * Sends source metadata to the AI and returns a concise reliability summary that
 * can be surfaced in the Browse screen as a quality indicator.
 *
 * @property aiRepository Repository for AI operations
 */
class AnalyzeSourceUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    /**
     * Analyze a manga source and return a quality summary.
     *
     * @param sourceName  Human-readable name of the source
     * @param sourceLanguage BCP-47 language tag or locale string (e.g. "en", "ja")
     * @param isNsfw     Whether the source is marked as NSFW
     * @return [Result] containing a short quality/reliability summary, or an error
     */
    suspend operator fun invoke(
        sourceName: String,
        sourceLanguage: String,
        isNsfw: Boolean
    ): Result<String> {
        if (sourceName.isBlank()) {
            return Result.failure(IllegalArgumentException("Source name cannot be blank"))
        }

        val prompt = buildString {
            append("You are a manga source quality analyst. ")
            append("Based solely on the metadata below, provide a very concise reliability assessment ")
            append("(1-2 sentences) for this manga source. ")
            append("Rate its likely reliability on a 1-5 scale and explain why.\n\n")
            append("Source name: $sourceName\n")
            append("Language: $sourceLanguage\n")
            append("NSFW content: ${if (isNsfw) "Yes" else "No"}\n\n")
            append("Format: '⭐ X/5 – <brief reason>'")
        }

        return aiRepository.generateContent(prompt)
    }
}
