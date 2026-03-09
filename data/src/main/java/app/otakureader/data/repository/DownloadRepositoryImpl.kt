package app.otakureader.data.repository

import android.content.Context
import app.otakureader.data.download.ChapterDownloadRequest
import app.otakureader.data.download.DownloadManager
import app.otakureader.data.download.DownloadProvider
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager
) : DownloadRepository {

    // Use a background dispatcher so notification updates don't run on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notifier = DownloadNotifier(context)

    init {
        // Keep the system notification in sync with the download queue.
        downloadManager.downloads
            .onEach { notifier.update(it) }
            .launchIn(scope)
    }

    override fun observeDownloads(): Flow<List<DownloadItem>> = downloadManager.downloads

    override suspend fun enqueueChapter(
        mangaId: Long,
        chapterId: Long,
        sourceName: String,
        mangaTitle: String,
        chapterTitle: String,
        pageUrls: List<String>
    ) {
        downloadManager.enqueue(
            ChapterDownloadRequest(
                mangaId = mangaId,
                chapterId = chapterId,
                sourceName = sourceName,
                mangaTitle = mangaTitle,
                chapterTitle = chapterTitle,
                pageUrls = pageUrls
            )
        )
    }

    override suspend fun pauseDownload(id: Long) {
        downloadManager.pause(id)
    }

    override suspend fun resumeDownload(id: Long) {
        downloadManager.resume(id)
    }

    override suspend fun cancelDownload(id: Long) {
        downloadManager.cancel(id)
    }

    override suspend fun deleteChapterDownload(
        chapterId: Long,
        sourceName: String,
        mangaTitle: String,
        chapterTitle: String
    ) {
        // Cancel any active job for this chapter before touching the filesystem.
        downloadManager.cancel(chapterId)

        withContext(Dispatchers.IO) {
            DownloadProvider.deleteChapter(context, sourceName, mangaTitle, chapterTitle)
        }
    }

    override suspend fun clearAll() {
        downloadManager.clearAll()
        notifier.cancel()
    }

    override suspend fun isChapterDownloaded(
        sourceName: String,
        mangaTitle: String,
        chapterTitle: String
    ): Boolean = withContext(Dispatchers.IO) {
        DownloadProvider.isChapterDownloaded(context, sourceName, mangaTitle, chapterTitle)
    }
}
