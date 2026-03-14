package app.otakureader.domain.usecase.ai

import app.otakureader.domain.repository.AiRepository

/**
 * Use case for generating AI-powered content.
 *
 * This use case provides a clean interface for features to interact
 * with AI capabilities, such as generating manga summaries, recommendations,
 * or other text-based content.
 *
 * @property aiRepository Repository for AI operations
 */
class GenerateAiContentUseCase(
    private val aiRepository: AiRepository
) {
    /**
     * Generate content based on the provided prompt.
     *
     * @param prompt The text prompt to send to the AI
     * @return Result containing the generated text on success, or an error
     */
    suspend operator fun invoke(prompt: String): Result<String> {
        if (prompt.isBlank()) {
            return Result.failure(IllegalArgumentException("Prompt cannot be blank"))
        }

        return aiRepository.generateContent(prompt)
    }
}
