package app.otakureader.domain.usecase

import app.otakureader.domain.repository.DownloadRepository
import javax.inject.Inject

/**
 * Removes a downloaded chapter from local storage and updates download state.
 */
class DeleteChapterUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(
        chapterId: Long,
        sourceName: String,
        mangaTitle: String,
        chapterTitle: String
    ) {
        downloadRepository.deleteChapterDownload(
            chapterId = chapterId,
            sourceName = sourceName,
            mangaTitle = mangaTitle,
            chapterTitle = chapterTitle
        )
    }
}
