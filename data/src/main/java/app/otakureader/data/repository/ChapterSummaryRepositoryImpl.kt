package app.otakureader.data.repository

import app.otakureader.domain.model.ChapterSummary
import app.otakureader.domain.repository.ChapterSummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [ChapterSummaryRepository].
 *
 * Summaries survive for the lifetime of the process. Because generating a summary
 * is a relatively expensive AI operation (latency + token cost), the cache avoids
 * regenerating the same summary multiple times within a session.
 *
 * If persistent storage is needed in a future release, this implementation can be
 * replaced with a Room-backed one without changing any callers.
 */
@Singleton
class ChapterSummaryRepositoryImpl @Inject constructor() : ChapterSummaryRepository {

    private val cache = ConcurrentHashMap<Long, ChapterSummary>()
    private val cacheVersion = MutableStateFlow(0L)

    override suspend fun getSummary(chapterId: Long): ChapterSummary? = cache[chapterId]

    override fun observeSummary(chapterId: Long): Flow<ChapterSummary?> {
        return cacheVersion.map { cache[chapterId] }
    }

    override suspend fun saveSummary(summary: ChapterSummary) {
        cache[summary.chapterId] = summary
        cacheVersion.update { it + 1 }
    }

    override suspend fun clearSummariesForManga(mangaId: Long) {
        val toRemove = cache.values.filter { it.mangaId == mangaId }.map { it.chapterId }
        toRemove.forEach { cache.remove(it) }
        cacheVersion.update { it + 1 }
    }
}
