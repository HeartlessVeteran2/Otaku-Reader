package app.otakureader.domain.usecase

import app.otakureader.domain.repository.MangaRepository
import javax.inject.Inject

/**
 * Use case for updating a manga's personal notes.
 */
class UpdateMangaNoteUseCase @Inject constructor(
    private val mangaRepository: MangaRepository
) {
    suspend operator fun invoke(mangaId: Long, notes: String?) {
        mangaRepository.updateMangaNote(mangaId, notes)
    }
}
