package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.model.OcrTranslation
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.OcrTranslationRepository
import javax.inject.Inject

/**
 * Detects and translates text on a manga page using Gemini Vision.
 *
 * Unlike [TranslateSfxUseCase] which is scoped to onomatopoeia/sound effects,
 * this use case targets all visible text on the page (speech bubbles, narration
 * boxes, signage, etc.) and is intended for users who read scanlated content in
 * languages they cannot read.
 *
 * The use case:
 * 1. Checks the [AiFeatureGate] for [AiFeature.OCR_TRANSLATION].
 * 2. Returns the cached result from [OcrTranslationRepository] if one exists.
 *    A cached *empty* list is a valid result (the page has no text) and will not
 *    trigger a redundant AI call.
 * 3. Otherwise, sends the page image bytes to Gemini Vision with a structured
 *    prompt asking for one entry per text region.
 * 4. Persists the result so subsequent calls for the same page are instant.
 *
 * **Free-tier discipline**: This use case is intended to be called on-demand for
 * a single page only — never as a background batch — so the request rate stays
 * well within the `gemini-1.5-flash` free-tier quotas (15 requests/minute,
 * 1500 requests/day at the time of writing).
 *
 * **Graceful degradation**: When AI is unavailable (feature toggled off, no API
 * key, or backend error) the use case returns an empty list so the reader
 * continues to function normally.
 */
class TranslateOcrPageUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val aiFeatureGate: AiFeatureGate,
    private val ocrTranslationRepository: OcrTranslationRepository,
) {

    /**
     * Translate every visible text region on a single manga page.
     *
     * @param chapterId Database ID of the chapter.
     * @param pageIndex Zero-based page number within the chapter.
     * @param imageBytes JPEG/PNG-encoded page bytes (caller is responsible for
     *   downscaling to keep the request small — see the reader delegate).
     * @param targetLanguage Human-readable target language (default: "English").
     * @return [Result] containing a (possibly empty) list of [OcrTranslation]s.
     *   Returns an empty-list success instead of failure when AI is unavailable,
     *   so the reader can continue to display the page without disruption.
     */
    suspend operator fun invoke(
        chapterId: Long,
        pageIndex: Int,
        imageBytes: ByteArray,
        targetLanguage: String = "English",
    ): Result<List<OcrTranslation>> {
        if (!aiFeatureGate.isFeatureAvailable(AiFeature.OCR_TRANSLATION)) {
            return Result.success(emptyList())
        }

        // Return cached result if available.
        // A null means "not yet cached"; an empty list means "page has no text" — both
        // are distinct from a non-empty list. We only call the AI on a true cache miss.
        val cached = ocrTranslationRepository.getTranslations(chapterId, pageIndex)
        if (cached != null) {
            return Result.success(cached)
        }

        if (imageBytes.isEmpty()) {
            return Result.success(emptyList())
        }

        val prompt = buildPrompt(targetLanguage)
        val aiResult = aiRepository.generateContentWithImage(imageBytes, prompt)

        if (aiResult.isFailure) {
            return Result.failure(
                aiResult.exceptionOrNull() ?: RuntimeException("AI OCR translation failed")
            )
        }

        val translations = parseAiResponse(aiResult.getOrNull().orEmpty(), pageIndex)
        ocrTranslationRepository.saveTranslations(chapterId, pageIndex, translations)
        return Result.success(translations)
    }

    private fun buildPrompt(targetLanguage: String): String = buildString {
        appendLine("You are a manga translation assistant. The attached image is a single manga page.")
        appendLine()
        appendLine("Task: Identify every visible text region on the page — speech bubbles,")
        appendLine("narration boxes, signage, and any other readable text. Auto-detect the source")
        appendLine("language. Translate each region into $targetLanguage.")
        appendLine()
        appendLine("For each region provide:")
        appendLine("  1. The original text exactly as printed (preserve line breaks as spaces).")
        appendLine("  2. A natural, fluent $targetLanguage translation.")
        appendLine("  3. A confidence score from 0.00 to 1.00.")
        appendLine("  4. A brief position description (e.g. 'top-left bubble', 'centre narration').")
        appendLine()
        appendLine("Respond in this exact format (one entry per line, triple-pipe-separated):")
        appendLine("ORIGINAL|||TRANSLATION|||CONFIDENCE|||POSITION")
        appendLine()
        appendLine("Do not include any other commentary, headers, or markdown.")
        appendLine("If the page contains no readable text, respond with exactly: NONE")
    }

    /**
     * Parse the structured AI response into a list of [OcrTranslation] objects.
     *
     * Lines that don't conform to the expected format are silently skipped.
     * The position column is treated as optional continuation text — anything
     * after the third pipe is preserved to handle pipes inside the position hint.
     */
    internal fun parseAiResponse(response: String, pageIndex: Int): List<OcrTranslation> {
        val trimmed = response.trim()
        if (trimmed.equals("NONE", ignoreCase = true)) return emptyList()

        return trimmed.lines()
            .mapNotNull { line ->
                val parts = line.split("|||", limit = RESPONSE_PARTS_COUNT)
                // Require at least ORIGINAL, TRANSLATION, CONFIDENCE; POSITION is optional.
                if (parts.size < CONFIDENCE_INDEX + 1) return@mapNotNull null
                val confidence = parts[CONFIDENCE_INDEX].trim().toFloatOrNull() ?: return@mapNotNull null
                val original = parts[ORIGINAL_INDEX].trim()
                val translation = parts[TRANSLATION_INDEX].trim()
                if (original.isEmpty() || translation.isEmpty()) return@mapNotNull null
                OcrTranslation(
                    pageIndex = pageIndex,
                    originalText = original,
                    translatedText = translation,
                    confidence = confidence.coerceIn(0f, 1f),
                    positionHint = parts.getOrNull(POSITION_INDEX)?.trim()?.takeIf { it.isNotBlank() },
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
