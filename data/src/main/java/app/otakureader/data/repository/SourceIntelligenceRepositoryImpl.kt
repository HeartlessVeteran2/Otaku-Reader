package app.otakureader.data.repository

import app.otakureader.domain.model.SourceScore
import app.otakureader.domain.repository.SourceIntelligenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [SourceIntelligenceRepository].
 *
 * Scores are held for the lifetime of the process.  The AI analysis prompt includes
 * metadata that rarely changes (chapter count, latest chapter, language), so the
 * cache remains valid for a full session.
 */
@Singleton
class SourceIntelligenceRepositoryImpl @Inject constructor() : SourceIntelligenceRepository {

    /** Key: mangaId. Value: list of [SourceScore]s for that manga. */
    private val cache = ConcurrentHashMap<Long, List<SourceScore>>()
    private val cacheVersion = MutableStateFlow(0L)

    override suspend fun getScores(mangaId: Long): List<SourceScore> {
        return cache[mangaId] ?: emptyList()
    }

    override fun observeScores(mangaId: Long): Flow<List<SourceScore>> {
        return cacheVersion.map { cache[mangaId] ?: emptyList() }
    }

    override suspend fun saveScores(mangaId: Long, scores: List<SourceScore>) {
        cache[mangaId] = scores.sortedByDescending { it.overallScore }
        cacheVersion.update { it + 1 }
    }

    override suspend fun clearScores(mangaId: Long) {
        cache.remove(mangaId)
        cacheVersion.update { it + 1 }
    }
}
