package app.komikku.domain.usecase

import app.komikku.domain.model.ChapterWithHistory
import app.komikku.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for getting the user's reading history.
 */
class GetHistoryUseCase(
    private val chapterRepository: ChapterRepository
) {
    operator fun invoke(): Flow<List<ChapterWithHistory>> =
        chapterRepository.observeHistory()
}
