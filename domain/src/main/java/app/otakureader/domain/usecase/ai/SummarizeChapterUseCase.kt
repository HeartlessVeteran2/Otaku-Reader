package app.otakureader.domain.usecase.ai

import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.model.ChapterSummary
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.ChapterSummaryRepository
import javax.inject.Inject

/**
 * Generates (or retrieves a cached) AI summary for a manga chapter.
 *
 * The use case:
 * 1. Checks the [AiFeatureGate] for [AiFeature.SUMMARY_TRANSLATION].
 * 2. Returns the cached [ChapterSummary] from [ChapterSummaryRepository] when available.
 * 3. Otherwise, builds a structured prompt containing the chapter title and context,
 *    sends it to the AI, and persists the result.
 *
 * **Graceful degradation**: When AI is unavailable the use case returns
 * [Result.failure] with an [IllegalStateException] so callers can choose
 * to show a "summary unavailable" placeholder rather than crashing.
 */
class SummarizeChapterUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val aiFeatureGate: AiFeatureGate,
    private val chapterSummaryRepository: ChapterSummaryRepository,
) {

    /**
     * Generate or retrieve a summary for the given chapter.
     *
     * @param chapterId Database ID of the chapter.
     * @param mangaTitle Title of the parent manga (used in the AI prompt).
     * @param chapterName Display name of the chapter (e.g. "Chapter 42: The Final Battle").
     * @param precedingChapterTitles Ordered list of the most-recent prior chapter names used to
     *   give the AI enough narrative context. May be empty.
     * @param language BCP-47 tag for the desired output language (default: "en").
     * @return [Result] containing the [ChapterSummary], or an error if AI is unavailable
     *   or the request fails.
     */
    suspend operator fun invoke(
        chapterId: Long,
        mangaId: Long,
        mangaTitle: String,
        chapterName: String,
        precedingChapterTitles: List<String> = emptyList(),
        language: String = "en",
    ): Result<ChapterSummary> {
        if (!aiFeatureGate.isFeatureAvailable(AiFeature.SUMMARY_TRANSLATION)) {
            return Result.failure(
                IllegalStateException("Summary translation is not enabled.")
            )
        }

        // Return cached summary if available
        chapterSummaryRepository.getSummary(chapterId)?.let { cached ->
            return Result.success(cached)
        }

        val prompt = buildPrompt(mangaTitle, chapterName, precedingChapterTitles, language)
        val aiResult = aiRepository.generateContent(prompt)

        if (aiResult.isFailure) {
            return Result.failure(
                aiResult.exceptionOrNull()
                    ?: IllegalStateException("AI content generation failed.")
            )
        }

        val summaryText = aiResult.getOrNull()?.trim().orEmpty()
        if (summaryText.isBlank()) {
            return Result.failure(IllegalStateException("AI returned an empty summary."))
        }

        val summary = ChapterSummary(
            chapterId = chapterId,
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            chapterName = chapterName,
            summary = summaryText,
            language = language,
        )
        chapterSummaryRepository.saveSummary(summary)
        return Result.success(summary)
    }

    private fun buildPrompt(
        mangaTitle: String,
        chapterName: String,
        precedingChapters: List<String>,
        language: String,
    ): String = buildString {
        appendLine("You are a manga content assistant. Provide a concise, spoiler-aware summary.")
        appendLine("Respond in the following language: $language")
        appendLine()
        appendLine("Manga: $mangaTitle")
        appendLine("Chapter: $chapterName")

        if (precedingChapters.isNotEmpty()) {
            appendLine()
            appendLine("Recent preceding chapters (for context):")
            precedingChapters.takeLast(MAX_CONTEXT_CHAPTERS).forEach { title ->
                appendLine("  - $title")
            }
        }

        appendLine()
        appendLine("Write a 2-3 sentence summary of what likely happens in \"$chapterName\".")
        appendLine("Keep the tone neutral and avoid major spoilers from later chapters.")
    }

    companion object {
        private const val MAX_CONTEXT_CHAPTERS = 5
    }
}
