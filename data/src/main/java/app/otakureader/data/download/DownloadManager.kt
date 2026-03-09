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
 * Chapters are added to an in-memory queue backed by a [StateFlow]. Pages for a single chapter
 * are downloaded sequentially, but multiple chapters may be downloaded concurrently (typically
 * one coroutine per chapter, and callers may enqueue many at once).
 *
 * Already-downloaded pages are skipped automatically (provided the file is non-empty),
 * making the process idempotent. Partial files from interrupted downloads are re-downloaded.
 *
 * All mutations to [jobs] and [requests] are performed under [mutex] to prevent races.
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

    /** Stored requests keyed by chapterId so that paused/failed/completed downloads can be resumed. */
    private val requests = mutableMapOf<Long, ChapterDownloadRequest>()

    /** Internal map for O(1) lookup and updates of download items by chapterId. */
    private val downloadMap = mutableMapOf<Long, DownloadItem>()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    suspend fun enqueue(request: ChapterDownloadRequest) {
        mutex.withLock {
            val existing = downloadMap[request.chapterId]
            // Allow re-enqueueing for terminal states (COMPLETED, FAILED) or when the item
            // is not present at all (i.e., never queued, or previously canceled via cancel()
            // which removes the item). Active (QUEUED, DOWNLOADING) and PAUSED downloads
            // are not re-enqueued; use resume() for PAUSED.
            if (existing != null &&
                existing.status != DownloadStatus.COMPLETED &&
                existing.status != DownloadStatus.FAILED
            ) return

            requests[request.chapterId] = request
            val newItem = DownloadItem(
                id = request.chapterId,
                mangaId = request.mangaId,
                chapterId = request.chapterId,
                mangaTitle = request.mangaTitle,
                chapterTitle = request.chapterTitle,
                status = DownloadStatus.QUEUED
            )
            downloadMap[request.chapterId] = newItem
            _downloads.value = downloadMap.values.toList()
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
            val item = downloadMap[chapterId]
            if (item?.status != DownloadStatus.PAUSED) return
            requests[chapterId]
        } ?: return

        startDownload(request)
    }

    suspend fun cancel(chapterId: Long) {
        mutex.withLock {
            jobs.remove(chapterId)?.cancel()
            requests.remove(chapterId)
            downloadMap.remove(chapterId)
            _downloads.value = downloadMap.values.toList()
        }
    }

    /**
     * Removes a completed or paused download and its in-memory metadata.
     * This is used when the on-disk chapter is deleted.
     */
    suspend fun remove(chapterId: Long) {
        mutex.withLock {
            jobs.remove(chapterId)?.cancel()
            requests.remove(chapterId)
            downloadMap.remove(chapterId)
            _downloads.value = downloadMap.values.toList()
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            requests.clear()
            downloadMap.clear()
            _downloads.value = emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private suspend fun startDownload(request: ChapterDownloadRequest) {
        val chapterId = request.chapterId

        mutex.withLock {
            // Cancel any existing job for this chapter
            jobs.remove(chapterId)?.cancel()

            // Bail out if the item was removed while waiting for the lock
            if (!downloadMap.containsKey(chapterId)) return

            updateStatus(chapterId, DownloadStatus.DOWNLOADING)

            // Launch and register the job under the same lock to eliminate the window
            // where cancel() could run before the job is stored in `jobs`.
            jobs[chapterId] = scope.launch {
                try {
                    val pageUrls = request.pageUrls
                    val totalPages = pageUrls.size

                    if (totalPages == 0) {
                        // No pages provided yet – keep the chapter queued so it can be retried
                        // later when the caller supplies the actual URLs.
                        updateStatus(chapterId, DownloadStatus.QUEUED)
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

                        // Download only if the file is missing or empty (partial write).
                        if (!destFile.exists() || destFile.length() == 0L) {
                            // Remove a partial file so the download starts from scratch.
                            destFile.delete()

                            val result = downloader.downloadPage(url, destFile)
                            if (result.isFailure) {
                                // Clean up any partial write so future retries start fresh.
                                destFile.delete()
                                updateStatus(chapterId, DownloadStatus.FAILED)
                                return@launch
                            }
                        }

                        // Always update progress whether the file was downloaded or already existed.
                        updateProgress(chapterId, ((index + 1) * 100) / totalPages)
                    }

                    if (isActive) {
                        updateStatus(chapterId, DownloadStatus.COMPLETED)
                    }
                } finally {
                    // Always remove the job reference on all terminal paths (success, failure,
                    // cancellation, or early return) to prevent stale entries.
                    mutex.withLock { jobs.remove(chapterId) }
                }
            }
        }
    }

    private fun updateStatus(chapterId: Long, status: DownloadStatus) {
        downloadMap[chapterId]?.let { item ->
            downloadMap[chapterId] = item.copy(status = status)
            _downloads.value = downloadMap.values.toList()
        }
    }

    private fun updateProgress(chapterId: Long, progress: Int) {
        downloadMap[chapterId]?.let { item ->
            downloadMap[chapterId] = item.copy(progress = progress)
            _downloads.value = downloadMap.values.toList()
        }
    }
}
