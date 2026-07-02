package app.otakureader.feature.reader.viewmodel.delegate

import android.content.Context
import android.util.Log
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveDownloadFolderName
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.sourceapi.Page
import app.otakureader.sourceapi.SourceChapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class ReaderDownloadAheadDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadPreferences: DownloadPreferences,
    private val downloadRepository: DownloadRepository,
    private val sourceRepository: SourceRepository,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
) {
    suspend fun enqueueCurrentChapter(manga: Manga, chapter: Chapter): Boolean {
        val sourceIdString = manga.sourceId.toString()
        val downloadFolderName = sourceRepository.resolveDownloadFolderName(manga.sourceId)
        val existingDownloads = downloadRepository.observeDownloads().first()
        if (existingDownloads.any { it.chapterId == chapter.id }) return false
        if (downloadRepository.isChapterDownloaded(downloadFolderName, manga.title, chapter.name)) return false
        return tryEnqueueChapter(manga, chapter, sourceIdString, downloadFolderName, existingDownloads)
    }

    suspend fun isChapterDownloaded(manga: Manga, chapter: Chapter): Boolean =
        downloadRepository.isChapterDownloaded(
            sourceRepository.resolveDownloadFolderName(manga.sourceId),
            manga.title,
            chapter.name,
        )

    fun maybeDownloadNextChapter(
        scope: CoroutineScope,
        currentPage: Int,
        totalPages: Int,
        mangaId: Long,
        chapterId: Long,
        getCurrentManga: () -> Manga?,
    ) {
        if (totalPages == 0) return
        val progress = currentPage.toFloat() / totalPages
        if (progress < PROGRESS_THRESHOLD) return

        scope.launch {
            val downloadAheadChapters = downloadPreferences.downloadAheadWhileReading.first()
            if (downloadAheadChapters <= 0) return@launch

            val onlyOnWifi = downloadPreferences.downloadAheadOnlyOnWifi.first()
            if (onlyOnWifi && !isOnWifi()) return@launch

            val chapters = chapterRepository.getChaptersByMangaId(mangaId).first()
            val currentIndex = chapters.indexOfFirst { it.id == chapterId }
            if (currentIndex == -1 || currentIndex >= chapters.size - 1) return@launch

            val manga = getCurrentManga() ?: mangaRepository.getMangaById(mangaId) ?: return@launch
            val sourceIdString = manga.sourceId.toString()
            val downloadFolderName = sourceRepository.resolveDownloadFolderName(manga.sourceId)
            val existingDownloads = downloadRepository.observeDownloads().first()

            val endIndex = minOf(currentIndex + downloadAheadChapters, chapters.size - 1)
            for (i in currentIndex + 1..endIndex) {
                tryEnqueueChapter(manga, chapters[i], sourceIdString, downloadFolderName, existingDownloads)
            }
        }
    }

    private suspend fun tryEnqueueChapter(
        manga: Manga,
        chapter: Chapter,
        sourceIdString: String,
        downloadFolderName: String,
        existingDownloads: List<DownloadItem>,
    ): Boolean {
        if (existingDownloads.any { it.chapterId == chapter.id }) return false
        if (downloadRepository.isChapterDownloaded(downloadFolderName, manga.title, chapter.name)) return false

        val sourceChapter = SourceChapter(
            url = chapter.url,
            name = chapter.name,
            dateUpload = chapter.dateUpload,
            chapterNumber = chapter.chapterNumber,
            scanlator = chapter.scanlator ?: "",
        )
        val pageListResult = sourceRepository.getPageList(sourceIdString, sourceChapter)
        pageListResult.onFailure { throwable ->
            runCatching {
                Log.w(TAG, "Failed to fetch page list for download-ahead " +
                    "(mangaId=${manga.id}, chapterId=${chapter.id})", throwable)
            }
        }

        val pageUrls = pageListResult.getOrNull()
            ?.mapNotNull { page -> page.effectiveUrl() }
            .orEmpty()
        if (pageUrls.isEmpty()) return false

        downloadRepository.enqueueChapter(
            mangaId = manga.id,
            chapterId = chapter.id,
            sourceName = downloadFolderName,
            mangaTitle = manga.title,
            chapterTitle = chapter.name,
            pageUrls = pageUrls,
        )
        return true
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return false
        val networkCapabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun Page.effectiveUrl(): String? = when {
        !imageUrl.isNullOrBlank() -> imageUrl
        url.isNotBlank() -> url
        else -> null
    }

    companion object {
        private const val TAG = "ReaderDownloadAheadDelegate"
        private const val PROGRESS_THRESHOLD = 0.8f
    }
}
