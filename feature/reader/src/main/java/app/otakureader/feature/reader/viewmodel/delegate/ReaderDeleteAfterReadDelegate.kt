package app.otakureader.feature.reader.viewmodel.delegate

import app.otakureader.core.preferences.DeleteAfterReadMode
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.resolveShouldDeleteAfterRead
import app.otakureader.domain.download.selectChapterToDeleteAfterRead
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveDownloadFolderName
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Deletes a chapter's downloaded files once it has been marked read, honouring the global
 * "delete after reading" preference and any per-manga override.
 *
 * The global toggle and per-manga override (surfaced in Settings > Downloads and the manga
 * detail screen, respectively) previously had no consumer anywhere in the app — the DataStore
 * values were written and read back by the settings UI, but nothing acted on them, so toggling
 * "delete after reading" silently did nothing.
 */
class ReaderDeleteAfterReadDelegate @Inject constructor(
    private val downloadPreferences: DownloadPreferences,
    private val downloadRepository: DownloadRepository,
    private val sourceRepository: SourceRepository,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
) {
    suspend fun maybeDeleteAfterRead(mangaId: Long, chapterId: Long) {
        val overrideMode = downloadPreferences.perMangaOverrides.first()[mangaId] ?: DeleteAfterReadMode.INHERIT
        val shouldDelete = resolveShouldDeleteAfterRead(
            overrideMode = overrideMode,
            globalEnabled = downloadPreferences.deleteAfterReading.first(),
        )
        if (!shouldDelete) return

        val manga = mangaRepository.getMangaById(mangaId) ?: return
        // slots > 0 keeps the last N read chapters downloaded and deletes the one N back instead.
        val slots = downloadPreferences.removeAfterReadSlots.first()
        val chapter = if (slots <= 0) {
            chapterRepository.getChapterById(chapterId)
        } else {
            selectChapterToDeleteAfterRead(
                chapters = chapterRepository.getChaptersByMangaIdSync(mangaId),
                justReadChapterId = chapterId,
                slots = slots,
            )
        } ?: return
        val downloadFolderName = sourceRepository.resolveDownloadFolderName(manga.sourceId)
        if (!downloadRepository.isChapterDownloaded(downloadFolderName, manga.title, chapter.name)) return

        downloadRepository.deleteChapterDownload(
            chapterId = chapter.id,
            sourceName = downloadFolderName,
            mangaTitle = manga.title,
            chapterTitle = chapter.name,
        )
    }
}
