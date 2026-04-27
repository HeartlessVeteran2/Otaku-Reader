package app.otakureader.domain.usecase

import app.otakureader.domain.model.ContinueReadingItem
import app.otakureader.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for observing the "Continue Reading" items shown in the Library screen carousel.
 * Returns the most recently read chapters for favorited manga, deduplicated per manga.
 */
class GetContinueReadingUseCase @Inject constructor(
    private val chapterRepository: ChapterRepository
) {
    operator fun invoke(): Flow<List<ContinueReadingItem>> =
        chapterRepository.observeContinueReading()
}
