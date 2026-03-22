package app.otakureader.domain.usecase

import app.otakureader.domain.model.LibraryManga
import app.otakureader.domain.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case that provides a filtered and searched library stream.
 * Search is delegated to the repository/DB layer to avoid loading the full library into memory.
 */
class GetLibraryUseCase @Inject constructor(
    private val mangaRepository: MangaRepository
) {
    operator fun invoke(query: String = ""): Flow<List<LibraryManga>> {
        val source = if (query.isBlank()) {
            mangaRepository.getLibraryManga()
        } else {
            mangaRepository.searchLibraryManga(query)
        }
        return source.map { mangas ->
            mangas.map { manga -> LibraryManga(manga = manga, unreadCount = manga.unreadCount) }
        }
    }
}
