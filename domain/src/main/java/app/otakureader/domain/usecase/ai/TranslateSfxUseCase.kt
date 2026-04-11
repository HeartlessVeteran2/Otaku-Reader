package app.otakureader.domain.usecase.ai

import app.otakureader.domain.repository.AiRepository
import javax.inject.Inject

/**
 * Use case for translating manga sound effects (SFX) using AI.
 *
 * Sends the raw SFX text (typically Japanese onomatopoeia) to the AI service
 * and returns a short English translation with a brief phonetic explanation.
 *
 * @property aiRepository Repository for AI operations
 */
class TranslateSfxUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    /**
     * Translate a manga sound effect.
     *
     * @param sfxText The raw sound effect text (e.g. "ドカン", "BOOM", "バン")
     * @return [Result] containing the translation, or an error
     */
    suspend operator fun invoke(sfxText: String): Result<String> {
        if (sfxText.isBlank()) {
            return Result.failure(IllegalArgumentException("SFX text cannot be blank"))
        }

        val prompt = buildString {
            append("You are a manga sound effect (SFX) translator. ")
            append("Translate the following manga sound effect to English. ")
            append("Provide: 1) The English meaning/translation, 2) What action or sound it represents. ")
            append("Keep the response concise (2-3 sentences max).\n\n")
            append("Sound effect: $sfxText")
        }

        return aiRepository.generateContent(prompt)
    }
}
