package app.otakureader.domain.model

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
