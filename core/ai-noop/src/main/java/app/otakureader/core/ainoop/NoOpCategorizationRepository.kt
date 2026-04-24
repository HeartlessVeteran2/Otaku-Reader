package app.otakureader.core.ainoop

import app.otakureader.domain.model.CategorizationResult
import app.otakureader.domain.repository.CategorizationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpCategorizationRepository @Inject constructor() : CategorizationRepository {

    override suspend fun saveCategorizationResult(result: CategorizationResult) { /* no-op */ }

    override suspend fun getCategorizationResult(mangaId: Long): CategorizationResult? = null

    override fun getCategorizationResultFlow(mangaId: Long): Flow<CategorizationResult?> = flowOf(null)

    override suspend fun deleteCategorizationResult(mangaId: Long) { /* no-op */ }

    override fun getPendingSuggestions(): Flow<List<CategorizationResult>> = flowOf(emptyList())

    override suspend fun markSuggestionsAsReviewed(mangaId: Long) { /* no-op */ }
}
