package app.otakureader.data.download

import android.content.Context
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds everything needed to download a chapter.
 *
 * @param pageUrls ordered list of remote image URLs; may be empty when pages have not
 *                 been resolved from the source yet.
 */
data class ChapterDownloadRequest(
    val mangaId: Long,
    val chapterId: Long,
    val sourceName: String,
    val mangaTitle: String,
    val chapterTitle: String,
    val pageUrls: List<String>
)

/**
 * Manages the chapter download queue and coordinates actual file downloads via [Downloader].
 *
 * Chapters are added to an in-memory queue backed by a [StateFlow]; each chapter's pages
 * are downloaded sequentially.  Multiple chapters can be queued at once but only one chapter
 * is actively downloading at a time (one coroutine per chapter, but callers may enqueue many).
 *
 * Already-downloaded pages are skipped automatically, making the process idempotent.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: Downloader
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    /** Active coroutine jobs keyed by chapterId. */
    private val jobs = mutableMapOf<Long, Job>()

    /** Stored requests keyed by chapterId so that paused downloads can be resumed. */
    private val requests = mutableMapOf<Long, ChapterDownloadRequest>()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    suspend fun enqueue(request: ChapterDownloadRequest) {
        mutex.withLock {
            val existing = _downloads.value.firstOrNull { it.chapterId == request.chapterId }
            if (existing != null && existing.status != DownloadStatus.CANCELED) return

            requests[request.chapterId] = request
            _downloads.update { list ->
                list.filterNot { it.chapterId == request.chapterId } + DownloadItem(
                    id = request.chapterId,
                    mangaId = request.mangaId,
                    chapterId = request.chapterId,
                    mangaTitle = request.mangaTitle,
                    chapterTitle = request.chapterTitle,
                    status = DownloadStatus.QUEUED
                )
            }
        }
        startDownload(request)
    }

    suspend fun pause(chapterId: Long) {
        mutex.withLock {
            jobs.remove(chapterId)?.cancel()
            updateStatus(chapterId, DownloadStatus.PAUSED)
        }
    }

    suspend fun resume(chapterId: Long) {
        val request = mutex.withLock {
            val item = _downloads.value.firstOrNull { it.chapterId == chapterId }
            if (item?.status != DownloadStatus.PAUSED) return
            requests[chapterId]
        } ?: return

        startDownload(request)
    }

    suspend fun cancel(chapterId: Long) {
        mutex.withLock {
            jobs.remove(chapterId)?.cancel()
            requests.remove(chapterId)
            _downloads.update { list -> list.filterNot { it.chapterId == chapterId } }
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            requests.clear()
            _downloads.value = emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun startDownload(request: ChapterDownloadRequest) {
        val chapterId = request.chapterId

        jobs.remove(chapterId)?.cancel()
        updateStatus(chapterId, DownloadStatus.DOWNLOADING)

        val job = scope.launch {
            val pageUrls = request.pageUrls
            val totalPages = pageUrls.size

            if (totalPages == 0) {
                // No pages provided yet – mark as completed so it can be retried later
                // when the caller supplies the actual URLs.
                updateStatus(chapterId, DownloadStatus.COMPLETED)
                mutex.withLock { jobs.remove(chapterId) }
                return@launch
            }

            pageUrls.forEachIndexed { index, url ->
                if (!isActive) return@launch

                val destFile = DownloadProvider.getPageFile(
                    context,
                    request.sourceName,
                    request.mangaTitle,
                    request.chapterTitle,
                    index
                )

                if (!destFile.exists()) {
                    val result = downloader.downloadPage(url, destFile)
                    if (result.isFailure) {
                        updateStatus(chapterId, DownloadStatus.FAILED)
                        return@launch
                    }
                }

                val progress = ((index + 1) * 100) / totalPages
                updateProgress(chapterId, progress)
            }

            if (isActive) {
                updateStatus(chapterId, DownloadStatus.COMPLETED)
                mutex.withLock { jobs.remove(chapterId) }
            }
        }

        jobs[chapterId] = job
    }

    private fun updateStatus(chapterId: Long, status: DownloadStatus) {
        _downloads.update { list ->
            list.map { if (it.chapterId == chapterId) it.copy(status = status) else it }
        }
    }

    private fun updateProgress(chapterId: Long, progress: Int) {
        _downloads.update { list ->
            list.map { if (it.chapterId == chapterId) it.copy(progress = progress) else it }
        }
    }
}
