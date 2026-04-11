package app.otakureader.domain.usecase.ai

import app.otakureader.domain.repository.AiRepository
import javax.inject.Inject

/**
 * Use case for generating manga summaries using AI.
 *
 * This use case provides specialized prompting for manga summary generation,
 * with appropriate token limits and temperature settings.
 *
 * @property aiRepository Repository for AI operations
 */
class GenerateMangaSummaryUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    /**
     * Generate a summary for manga description or chapter content.
     *
     * @param title The manga title
     * @param description The full description text to summarize
     * @param maxLength Maximum word count for the summary (default: 100)
     * @return [Result] containing the generated summary, or an error
     */
    suspend operator fun invoke(
        title: String,
        description: String,
        maxLength: Int = 100
    ): Result<String> {
        if (description.isBlank()) {
            return Result.failure(IllegalArgumentException("Description cannot be blank"))
        }

        val prompt = buildString {
            append("Summarize the following manga in $maxLength words or less. ")
            append("Focus on the main plot and genre without spoilers:\n\n")
            append("Title: $title\n")
            append("Description: $description")
        }

        return aiRepository.generateContent(prompt)
    }

    /**
     * Generate a summary for multiple chapters.
     *
     * @param title The manga title
     * @param chapterTitles List of recent chapter titles
     * @param maxLength Maximum word count for the summary
     * @return [Result] containing the arc summary, or an error
     */
    suspend fun summarizeArc(
        title: String,
        chapterTitles: List<String>,
        maxLength: Int = 150
    ): Result<String> {
        if (chapterTitles.isEmpty()) {
            return Result.failure(IllegalArgumentException("Chapter titles cannot be empty"))
        }

        val prompt = buildString {
            append("Based on these chapter titles from '$title', ")
            append("summarize the current story arc in $maxLength words or less:\n\n")
            chapterTitles.forEach { append("- $it\n") }
        }

        return aiRepository.generateContent(prompt)
    }
}
