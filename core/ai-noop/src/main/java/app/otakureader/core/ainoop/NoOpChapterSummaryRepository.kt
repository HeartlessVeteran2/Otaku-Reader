package app.otakureader.core.ainoop

import app.otakureader.domain.model.ChapterSummary
import app.otakureader.domain.repository.ChapterSummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpChapterSummaryRepository @Inject constructor() : ChapterSummaryRepository {

    override suspend fun getSummary(chapterId: Long): ChapterSummary? = null

    override fun observeSummary(chapterId: Long): Flow<ChapterSummary?> = flowOf(null)

    override suspend fun saveSummary(summary: ChapterSummary) { /* no-op */ }

    override suspend fun clearSummariesForManga(mangaId: Long) { /* no-op */ }
}
