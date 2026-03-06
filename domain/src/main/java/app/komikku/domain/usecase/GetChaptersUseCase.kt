package app.komikku.domain.usecase

import app.komikku.domain.model.Chapter
import app.komikku.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for getting chapters of a manga, ordered for display.
 */
class GetChaptersUseCase(
    private val chapterRepository: ChapterRepository
) {
    operator fun invoke(mangaId: Long): Flow<List<Chapter>> =
        chapterRepository.observeChaptersByManga(mangaId)
}
