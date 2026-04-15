package app.otakureader.domain.repository

import app.otakureader.domain.model.CategorizationResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing AI categorization results.
 */
interface CategorizationRepository {
    /**
     * Save a categorization result for a manga.
     */
    suspend fun saveCategorizationResult(result: CategorizationResult)
    
    /**
     * Get the categorization result for a manga.
     */
    suspend fun getCategorizationResult(mangaId: Long): CategorizationResult?
    
    /**
     * Get categorization result as a flow.
     */
    fun getCategorizationResultFlow(mangaId: Long): Flow<CategorizationResult?>
    
    /**
     * Delete categorization result for a manga.
     */
    suspend fun deleteCategorizationResult(mangaId: Long)
    
    /**
     * Get all pending suggestions (those not auto-applied).
     */
    fun getPendingSuggestions(): Flow<List<CategorizationResult>>
    
    /**
     * Mark suggestions as reviewed for a manga.
     */
    suspend fun markSuggestionsAsReviewed(mangaId: Long)
}
