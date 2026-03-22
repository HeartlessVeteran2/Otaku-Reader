package app.otakureader.domain.usecase

import app.otakureader.domain.model.Chapter
import app.otakureader.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for getting chapters of a manga, ordered for display.
 */
class GetChaptersUseCase @Inject constructor(
    private val chapterRepository: ChapterRepository
) {
    operator fun invoke(mangaId: Long): Flow<List<Chapter>> =
        chapterRepository.getChaptersByMangaId(mangaId)
}
