package app.otakureader.domain.usecase

import app.otakureader.domain.model.MangaUpdate
import app.otakureader.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Returns a live list of recently fetched chapters paired with their manga. */
class GetRecentUpdatesUseCase @Inject constructor(
    private val chapterRepository: ChapterRepository
) {
    operator fun invoke(): Flow<List<MangaUpdate>> = chapterRepository.getRecentUpdates()
}
