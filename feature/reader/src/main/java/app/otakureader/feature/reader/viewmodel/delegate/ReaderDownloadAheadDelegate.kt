package app.otakureader.feature.reader.viewmodel.delegate

import android.content.Context
import android.util.Log
import app.otakureader.data.download.ChapterDownloadRequest
import app.otakureader.data.download.DownloadManager
import app.otakureader.data.download.DownloadProvider
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
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
    private val downloadManager: DownloadManager,
    private val sourceRepository: SourceRepository,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
) {
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
            val nextChapter = chapters[currentIndex + 1]

            val existingDownload = downloadManager.downloads.first().find { it.chapterId == nextChapter.id }
            if (existingDownload != null) return@launch

            val manga = getCurrentManga() ?: mangaRepository.getMangaById(mangaId) ?: return@launch
            val sourceName = manga.sourceId.toString()
            if (DownloadProvider.isChapterDownloaded(context, sourceName, manga.title, nextChapter.name)) return@launch

            val sourceChapter = SourceChapter(
                url = nextChapter.url,
                name = nextChapter.name,
                dateUpload = nextChapter.dateUpload,
                chapterNumber = nextChapter.chapterNumber,
                scanlator = nextChapter.scanlator,
            )
            val pageListResult = sourceRepository.getPageList(sourceName, sourceChapter)
            pageListResult.onFailure { throwable ->
                runCatching {
                    Log.w(TAG, "Failed to fetch page list for download-ahead " +
                        "(mangaId=${manga.id}, chapterId=${nextChapter.id})", throwable)
                }
            }

            val pageUrls = pageListResult.getOrNull()
                ?.mapNotNull { page -> page.effectiveUrl() }
                .orEmpty()
            if (pageUrls.isEmpty()) return@launch

            downloadManager.enqueue(
                ChapterDownloadRequest(
                    mangaId = manga.id,
                    chapterId = nextChapter.id,
                    sourceName = sourceName,
                    mangaTitle = manga.title,
                    chapterTitle = nextChapter.name,
                    pageUrls = pageUrls,
                )
            )
        }
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
