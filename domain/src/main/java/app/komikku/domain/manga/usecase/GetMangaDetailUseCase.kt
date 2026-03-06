package app.komikku.domain.manga.usecase

import app.komikku.domain.manga.model.Manga
import app.komikku.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMangaDetailUseCase @Inject constructor(
    private val mangaRepository: MangaRepository,
) {
    operator fun invoke(mangaId: Long): Flow<Manga?> = mangaRepository.getMangaById(mangaId)
}
