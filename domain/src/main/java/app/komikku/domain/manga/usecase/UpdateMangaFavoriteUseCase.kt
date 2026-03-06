package app.komikku.domain.manga.usecase

import app.komikku.domain.manga.repository.MangaRepository
import javax.inject.Inject

class UpdateMangaFavoriteUseCase @Inject constructor(
    private val mangaRepository: MangaRepository,
) {
    suspend operator fun invoke(mangaId: Long, isFavorite: Boolean) {
        mangaRepository.updateFavorite(mangaId, isFavorite)
    }
}
