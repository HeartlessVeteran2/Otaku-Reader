package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.model.SfxTranslation
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.SfxTranslationRepository
import javax.inject.Inject

/**
 * Detects and translates sound effects (SFX) on a manga page using AI.
 *
 * The use case:
 * 1. Checks the [AiFeatureGate] for [AiFeature.SFX_TRANSLATION].
 * 2. Returns the cached result from [SfxTranslationRepository] if one exists.
 *    A cached *empty* list is a valid result (the page has no SFX) and will not
 *    trigger a redundant AI call.
 * 3. Otherwise, sends a structured prompt to the AI asking it to identify SFX
 *    in the page and translate them into the requested [targetLanguage].
 * 4. Persists the result so subsequent calls for the same page are instant.
 *
 * **Graceful degradation**: When AI is unavailable (feature toggled off, no API key,
 * or backend error) the use case returns an empty list so the reader continues
 * to function normally without any SFX overlay.
 */
class TranslateSfxUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val aiFeatureGate: AiFeatureGate,
    private val sfxTranslationRepository: SfxTranslationRepository,
) {

    /**
     * Translate SFX on a single manga page.
     *
     * @param chapterId Database ID of the chapter.
     * @param pageIndex Zero-based page number within the chapter.
     * @param pageImageUrl Public URL (or local URI) of the page image.
     *   Used as context in the AI prompt; the AI is not sent the raw image bytes.
     * @param targetLanguage Human-readable target language (default: "English").
     * @return [Result] containing a (possibly empty) list of [SfxTranslation]s.
     *   Returns an empty-list success instead of failure when AI is unavailable,
     *   so the reader can continue loading without disruption.
     */
    suspend operator fun invoke(
        chapterId: Long,
        pageIndex: Int,
        pageImageUrl: String,
        targetLanguage: String = "English",
    ): Result<List<SfxTranslation>> {
        if (!aiFeatureGate.isFeatureAvailable(AiFeature.SFX_TRANSLATION)) {
            return Result.success(emptyList())
        }

        // Return cached result if available.
        // A null means "not yet cached"; an empty list means "page has no SFX" — both
        // are distinct from a non-empty list. We only call the AI on a true cache miss.
        val cached = sfxTranslationRepository.getTranslations(chapterId, pageIndex)
        if (cached != null) {
            return Result.success(cached)
        }

        val prompt = buildPrompt(pageImageUrl, targetLanguage)
        val aiResult = aiRepository.generateContent(prompt)

        if (aiResult.isFailure) {
            return Result.success(emptyList())
        }

        val translations = parseAiResponse(aiResult.getOrNull().orEmpty(), pageIndex)
        sfxTranslationRepository.saveTranslations(chapterId, pageIndex, translations)
        return Result.success(translations)
    }

    private fun buildPrompt(pageImageUrl: String, targetLanguage: String): String = buildString {
        appendLine("You are a manga localisation assistant. A manga page is available at: $pageImageUrl")
        appendLine()
        appendLine("Task: Identify every sound effect (SFX / onomatopoeia) visible on this page.")
        appendLine("For each SFX, provide:")
        appendLine("  1. The original text (as printed).")
        appendLine("  2. A $targetLanguage translation or equivalent sound word.")
        appendLine("  3. A confidence score from 0.00 to 1.00.")
        appendLine("  4. A brief position description (e.g. 'top-left', 'centre panel').")
        appendLine()
        appendLine("Respond in this exact format (one entry per line, pipe-separated):")
        appendLine("ORIGINAL|TRANSLATION|CONFIDENCE|POSITION")
        appendLine()
        appendLine("If no SFX are found, respond with exactly: NONE")
    }

    /**
     * Parse the structured AI response into a list of [SfxTranslation] objects.
     *
     * Lines that don't conform to the expected format are silently skipped.
     * The position column is treated as optional continuation text — anything
     * after the third pipe is joined back to handle pipes inside the position hint.
     */
    internal fun parseAiResponse(response: String, pageIndex: Int): List<SfxTranslation> {
        val trimmed = response.trim()
        if (trimmed.equals("NONE", ignoreCase = true)) return emptyList()

        return trimmed.lines()
            .mapNotNull { line ->
                // Split with a limit so trailing pipes in position text are preserved.
                val parts = line.split("|", limit = RESPONSE_PARTS_COUNT)
                if (parts.size < RESPONSE_PARTS_COUNT) return@mapNotNull null
                val confidence = parts[CONFIDENCE_INDEX].trim().toFloatOrNull() ?: return@mapNotNull null
                SfxTranslation(
                    pageIndex = pageIndex,
                    originalText = parts[ORIGINAL_INDEX].trim(),
                    translatedText = parts[TRANSLATION_INDEX].trim(),
                    confidence = confidence.coerceIn(0f, 1f),
                    positionHint = parts[POSITION_INDEX].trim().takeIf { it.isNotBlank() },
                )
            }
    }

    companion object {
        private const val RESPONSE_PARTS_COUNT = 4
        private const val ORIGINAL_INDEX = 0
        private const val TRANSLATION_INDEX = 1
        private const val CONFIDENCE_INDEX = 2
        private const val POSITION_INDEX = 3
    }
}
