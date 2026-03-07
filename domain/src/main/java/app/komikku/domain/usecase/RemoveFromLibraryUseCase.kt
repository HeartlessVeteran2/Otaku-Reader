package app.komikku.domain.usecase

import app.komikku.domain.repository.MangaRepository

/**
 * Use case to remove multiple manga from the library by unsetting their favorite status.
 */
class RemoveFromLibraryUseCase(
    private val mangaRepository: MangaRepository
) {
    suspend operator fun invoke(mangaIds: Set<Long>) {
        for (id in mangaIds) {
            mangaRepository.setFavorite(id, false)
        }
    }
}
