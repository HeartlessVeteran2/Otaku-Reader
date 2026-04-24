package app.otakureader.core.ainoop

import app.otakureader.domain.model.SourceScore
import app.otakureader.domain.repository.SourceIntelligenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpSourceIntelligenceRepository @Inject constructor() : SourceIntelligenceRepository {

    override suspend fun getScores(mangaId: Long): List<SourceScore> = emptyList()

    override fun observeScores(mangaId: Long): Flow<List<SourceScore>> = flowOf(emptyList())

    override suspend fun saveScores(mangaId: Long, scores: List<SourceScore>) { /* no-op */ }

    override suspend fun clearScores(mangaId: Long) { /* no-op */ }
}
