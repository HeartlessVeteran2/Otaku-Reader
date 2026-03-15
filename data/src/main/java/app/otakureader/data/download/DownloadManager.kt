package app.otakureader.data.download

import android.content.Context
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.DownloadPriority
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
import kotlinx.coroutines.flow.first
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
 * @param priority queue priority for this request; lower values are processed first.
 *                 Defaults to [DownloadPriority.NORMAL].
 */
data class ChapterDownloadRequest(
    val mangaId: Long,
    val chapterId: Long,
    val sourceName: String,
    val mangaTitle: String,
    val chapterTitle: String,
    val pageUrls: List<String>,
    val priority: Int = DownloadPriority.NORMAL
)

/**
 * Manages the chapter download queue and coordinates actual file downloads via [Downloader].
 *
 * Chapters are added to an in-memory queue backed by a [StateFlow]. Pages for a single chapter
 * are downloaded sequentially, but multiple chapters may be downloaded concurrently (typically
 * one coroutine per chapter, and callers may enqueue many at once).
 *
 * The queue is sorted by [DownloadItem.priority] (ascending – lower value = higher priority)
 * before being emitted.  Items with equal priority are ordered by insertion time (FIFO).
 *
 * Already-downloaded pages are skipped automatically (provided the file is non-empty),
 * making the process idempotent. Partial files from interrupted downloads are re-downloaded.
 *
 * All mutations to [jobs] and [requests] are performed under [mutex] to prevent races.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: Downloader,
    private val downloadPreferences: DownloadPreferences
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
                status = DownloadStatus.QUEUED,
                priority = request.priority
            )
            downloadMap[request.chapterId] = newItem
            refreshDownloadsList()
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
            refreshDownloadsList()
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
            refreshDownloadsList()
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

    /**
     * Moves the given chapter to the front of the in-memory queue by assigning it a
     * priority value lower than all currently queued items.
     *
     * This affects the ordering of the emitted [downloads] list, but does not interrupt
     * or preempt already-running downloads in the current implementation.
     *
     * If the chapter is not in the queue this is a no-op.
     */
    suspend fun prioritize(chapterId: Long) {
        mutex.withLock {
            val item = downloadMap[chapterId] ?: return
            val minPriority = downloadMap.values
                .filter { it.chapterId != chapterId }
                .minOfOrNull { it.priority } ?: DownloadPriority.NORMAL
            val newPriority = if (minPriority > Int.MIN_VALUE) minPriority - 1 else Int.MIN_VALUE
            downloadMap[chapterId] = item.copy(priority = newPriority)
            requests[chapterId]?.let { requests[chapterId] = it.copy(priority = newPriority) }
            refreshDownloadsList()
        }
    }

    /**
     * Sets an explicit [newPriority] value for the given chapter.
     *
     * Lower values appear earlier in the queue (higher urgency).  Use the constants in
     * [DownloadPriority] for common presets.  If the chapter is not in the queue this
     * is a no-op.
     */
    suspend fun reorder(chapterId: Long, newPriority: Int) {
        mutex.withLock {
            val item = downloadMap[chapterId] ?: return
            downloadMap[chapterId] = item.copy(priority = newPriority)
            requests[chapterId]?.let { requests[chapterId] = it.copy(priority = newPriority) }
            refreshDownloadsList()
        }
    }

    /**
     * Moves a list of chapters to the front of the queue in a single transaction.
     *
     * Each chapter in [chapterIds] receives a priority value lower than every chapter
     * that is not in the list. Chapters within the list retain their relative order from
     * the current queue. This is more efficient than calling [prioritize] repeatedly
     * because only one mutex acquisition and one list rebuild are required.
     *
     * IDs that are not currently in the queue are silently ignored.
     *
     * Duplicates in [chapterIds] are treated by first occurrence only.
     *
     * @param chapterIds Ordered list of chapter IDs to prioritize (defensive copy is made)
     */
    suspend fun prioritizeAll(chapterIds: List<Long>) {
        // Short-circuit: empty input is a no-op
        if (chapterIds.isEmpty()) return

        // Defensive copy to prevent concurrent mutation by caller
        val chapterIdsCopy = chapterIds.toList()
        mutex.withLock {
            val chapterIdSet = chapterIdsCopy.toHashSet()
            // Determine the targets in their current queue order.
            val orderedTargets = downloadMap.values
                .filter { it.chapterId in chapterIdSet }
                .sortedBy { it.priority }
            if (orderedTargets.isEmpty()) {
                // Nothing to prioritize; all IDs were absent from the queue.
                return@withLock
            }

            val outsideMin = downloadMap.values
                .filter { it.chapterId !in chapterIdSet }
                .minOfOrNull { it.priority } ?: DownloadPriority.NORMAL

            // How many distinct Int slots exist below outsideMin, using Long math to avoid overflow.
            val availableBelow = outsideMin.toLong() - Int.MIN_VALUE.toLong()

            if (availableBelow >= orderedTargets.size.toLong()) {
                // Enough room: assign contiguous priorities just below outsideMin.
                val base = outsideMin.toLong() - orderedTargets.size.toLong()
                orderedTargets.forEachIndexed { index, item ->
                    val newPriority = (base + index.toLong()).toInt()
                    downloadMap[item.chapterId] = item.copy(priority = newPriority)
                    requests[item.chapterId]?.let {
                        requests[item.chapterId] = it.copy(priority = newPriority)
                    }
                }
            } else {
                // Not enough room below outsideMin. Renormalize non-targets to non-negative
                // priorities, then place all targets in the Int.MIN_VALUE.. range so they
                // are strictly lower than every non-target.
                val nonTargets = downloadMap.values
                    .filter { it.chapterId !in chapterIdSet }
                    .sortedBy { it.priority }

                nonTargets.forEachIndexed { index, item ->
                    // Index is non-negative and well within Int range for realistic queues.
                    val normalizedPriority = index
                    downloadMap[item.chapterId] = item.copy(priority = normalizedPriority)
                    requests[item.chapterId]?.let {
                        requests[item.chapterId] = it.copy(priority = normalizedPriority)
                    }
                }

                orderedTargets.forEachIndexed { index, item ->
                    val newPriority = Int.MIN_VALUE + index
                    downloadMap[item.chapterId] = item.copy(priority = newPriority)
                    requests[item.chapterId]?.let {
                        requests[item.chapterId] = it.copy(priority = newPriority)
                    }
                }
            }

            refreshDownloadsList()
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
                        mutex.withLock { updateStatus(chapterId, DownloadStatus.QUEUED) }
                        return@launch
                    }

                    // Read the CBZ preference once before downloading to avoid
                    // repeated Flow emissions during the download loop.
                    val packAsCbz = downloadPreferences.saveAsCbz.first()

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
                                mutex.withLock { updateStatus(chapterId, DownloadStatus.FAILED) }
                                return@launch
                            }
                        }

                        // Always update progress whether the file was downloaded or already existed.
                        mutex.withLock { updateProgress(chapterId, ((index + 1) * 100) / totalPages) }
                    }

                    if (isActive) {
                        // Optionally pack pages into a CBZ archive when the preference is enabled.
                        if (packAsCbz) {
                            val chapterDir = DownloadProvider.getChapterDir(
                                context,
                                request.sourceName,
                                request.mangaTitle,
                                request.chapterTitle
                            )
                            CbzCreator.createCbz(chapterDir).onSuccess {
                                // Remove loose page files once the archive is created.
                                chapterDir.listFiles()
                                    ?.filter { file ->
                                        file.isFile &&
                                            file.extension.lowercase() in DownloadProvider.PAGE_EXTENSIONS
                                    }
                                    ?.forEach { it.delete() }
                            }
                        }
                        mutex.withLock {
                            updateStatus(chapterId, DownloadStatus.COMPLETED)
                        }
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
        updateDownloadInPlace(chapterId) { item ->
            item.copy(status = status)
        }
    }

    private fun updateProgress(chapterId: Long, progress: Int) {
        updateDownloadInPlace(chapterId) { item ->
            item.copy(progress = progress)
        }
    }

    /**
     * Updates a single [DownloadItem] in both [downloadMap] and [_downloads] without
     * re-sorting the entire list.  This preserves the existing ordering, which is
     * already sorted by [DownloadItem.priority] when items are enqueued or their
     * priority changes.
     *
     * Must be called while holding [mutex].
     */
    private fun updateDownloadInPlace(
        chapterId: Long,
        transform: (DownloadItem) -> DownloadItem
    ) {
        val current = downloadMap[chapterId] ?: return
        val updated = transform(current)
        downloadMap[chapterId] = updated

        _downloads.update { list ->
            list.map { item ->
                if (item.chapterId == chapterId) updated else item
            }
        }
    }

    /**
     * Rebuilds the public [downloads] list, sorted by [DownloadItem.priority] ascending
     * (lower value = higher priority).  Items with equal priority retain their original
     * insertion order (stable sort).
     *
     * Must be called while holding [mutex].
     */
    private fun refreshDownloadsList() {
        _downloads.value = downloadMap.values.sortedBy { it.priority }
    }
}
