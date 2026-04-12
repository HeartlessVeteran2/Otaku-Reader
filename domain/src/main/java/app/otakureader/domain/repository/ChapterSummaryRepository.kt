package app.otakureader.domain.repository

import app.otakureader.domain.model.ChapterSummary
import kotlinx.coroutines.flow.Flow

/**
 * Cache for AI-generated chapter summaries.
 *
 * Summaries are expensive to generate, so implementations should persist them
 * across app restarts (e.g., in-memory + optional DB backing).
 */
interface ChapterSummaryRepository {

    /**
     * Retrieve a cached summary for the given chapter, or `null` if none exists.
     */
    suspend fun getSummary(chapterId: Long): ChapterSummary?

    /**
     * Observe the summary for a chapter as a [Flow].
     *
     * Emits `null` until a summary is generated, then the [ChapterSummary].
     */
    fun observeSummary(chapterId: Long): Flow<ChapterSummary?>

    /**
     * Persist a generated summary, replacing any previously stored value.
     */
    suspend fun saveSummary(summary: ChapterSummary)

    /**
     * Remove all cached summaries for a manga (e.g., when the manga is removed from the library).
     *
     * @param mangaId The manga whose summaries should be purged.
     */
    suspend fun clearSummariesForManga(mangaId: Long)
}
