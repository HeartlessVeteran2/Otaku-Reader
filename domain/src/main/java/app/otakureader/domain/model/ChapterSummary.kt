package app.otakureader.domain.model

/**
 * AI-generated summary for a manga chapter.
 *
 * @property chapterId Database ID of the chapter this summary belongs to.
 * @property mangaId Database ID of the manga that owns this chapter.
 * @property mangaTitle Title of the manga (used in prompting and display).
 * @property chapterName Human-readable name of the chapter.
 * @property summary The AI-generated summary text.
 * @property language BCP-47 language tag of the summary (e.g. "en", "ja").
 * @property generatedAt Unix timestamp (ms) when the summary was generated.
 */
data class ChapterSummary(
    val chapterId: Long,
    val mangaId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val summary: String,
    val language: String = "en",
    val generatedAt: Long = System.currentTimeMillis(),
)
