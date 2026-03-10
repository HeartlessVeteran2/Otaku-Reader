package app.otakureader.domain.usecase

import app.otakureader.domain.repository.MangaRepository
import javax.inject.Inject

class SetMangaNotificationsUseCase @Inject constructor(
    private val mangaRepository: MangaRepository
) {
    suspend operator fun invoke(mangaId: Long, notify: Boolean) {
        mangaRepository.updateNotifyNewChapters(mangaId, notify)
    }
}
