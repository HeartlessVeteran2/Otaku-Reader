package app.otakureader.domain.repository

import app.otakureader.domain.model.SourceScore
import kotlinx.coroutines.flow.Flow

/**
 * Cache for AI-generated source quality scores.
 *
 * Scores are associated with a specific (manga, source) pair so that the
 * intelligence can be contextual (a source might be great for some titles but
 * poor for others).
 */
interface SourceIntelligenceRepository {

    /**
     * Retrieve all cached scores for a given manga, sorted by [SourceScore.overallScore] descending.
     *
     * @param mangaId The manga ID to look up.
     * @return Cached scores, or an empty list if none are stored.
     */
    suspend fun getScores(mangaId: Long): List<SourceScore>

    /**
     * Observe scores for a manga as a [Flow].
     *
     * Emits the latest list whenever it changes (e.g., after a fresh analysis).
     */
    fun observeScores(mangaId: Long): Flow<List<SourceScore>>

    /**
     * Persist a set of scores for a manga, replacing any previously stored values.
     */
    suspend fun saveScores(mangaId: Long, scores: List<SourceScore>)

    /**
     * Remove all cached scores for the given manga.
     */
    suspend fun clearScores(mangaId: Long)
}
