package app.otakureader.domain.usecase

import app.otakureader.domain.model.ChapterWithHistory
import app.otakureader.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for getting the user's reading history.
 */
class GetHistoryUseCase @Inject constructor(
    private val chapterRepository: ChapterRepository
) {
    operator fun invoke(): Flow<List<ChapterWithHistory>> =
        chapterRepository.observeHistory()
}
