package app.otakureader.data.repository

import android.content.Context
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.DownloadStatus
import app.otakureader.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    private val jobs = mutableMapOf<Long, Job>()
    private val mutex = Mutex()
    private val notifier = DownloadNotifier(context)

    override fun observeDownloads(): StateFlow<List<DownloadItem>> = downloads.asStateFlow()

    override suspend fun enqueueChapter(
        mangaId: Long,
        chapterId: Long,
        mangaTitle: String,
        chapterTitle: String
    ) {
        mutex.withLock {
            // Skip if already downloading or completed
            if (downloads.value.any { it.chapterId == chapterId && it.status != DownloadStatus.CANCELED }) {
                return
            }

            val item = DownloadItem(
                id = chapterId,
                mangaId = mangaId,
                chapterId = chapterId,
                mangaTitle = mangaTitle,
                chapterTitle = chapterTitle,
                status = DownloadStatus.QUEUED
            )
            downloads.update { it + item }
            startDownload(item.id)
        }
    }

    override suspend fun pauseDownload(id: Long) {
        mutex.withLock {
            downloads.update { list ->
                list.map { download ->
                    if (download.id == id && download.status == DownloadStatus.DOWNLOADING) {
                        download.copy(status = DownloadStatus.PAUSED)
                    } else {
                        download
                    }
                }
            }
            jobs.remove(id)?.cancel()
            updateNotification()
        }
    }

    override suspend fun resumeDownload(id: Long) {
        mutex.withLock {
            val target = downloads.value.firstOrNull { it.id == id } ?: return
            if (target.status != DownloadStatus.PAUSED) return

            startDownload(id)
        }
    }

    override suspend fun cancelDownload(id: Long) {
        mutex.withLock {
            jobs.remove(id)?.cancel()
            downloads.update { list -> list.filterNot { it.id == id } }
            updateNotification()
        }
    }

    override suspend fun clearAll() {
        mutex.withLock {
            jobs.values.forEach { job ->
                if (job.isActive) {
                    job.cancel()
                }
            }
            jobs.clear()
            downloads.value = emptyList()
            notifier.cancel()
        }
    }

    private fun startDownload(id: Long) {
        // Cancel any previous job for this download
        jobs.remove(id)?.cancel()

        downloads.update { list ->
            list.map { item ->
                if (item.id == id) {
                    item.copy(status = DownloadStatus.DOWNLOADING)
                } else {
                    item
                }
            }
        }
        updateNotification()

        val job = scope.launch {
            var completed = false
            while (isActive && !completed) {
                delay(400)
                downloads.update { list ->
                    list.map { download ->
                        if (download.id == id && download.status == DownloadStatus.DOWNLOADING) {
                            val increment = Random.nextInt(5, 12)
                            val progress = (download.progress + increment).coerceAtMost(100)
                            val status = if (progress >= 100) {
                                DownloadStatus.COMPLETED
                            } else {
                                DownloadStatus.DOWNLOADING
                            }
                            if (status == DownloadStatus.COMPLETED) {
                                completed = true
                            }
                            download.copy(progress = progress, status = status)
                        } else {
                            download
                        }
                    }
                }
                updateNotification()

                val current = downloads.value.firstOrNull { it.id == id } ?: break
                if (current.status != DownloadStatus.DOWNLOADING && current.status != DownloadStatus.COMPLETED) {
                    break
                }
            }

            if (completed) {
                mutex.withLock {
                    jobs.remove(id)
                }
                updateNotification()
            }
        }

        jobs[id] = job
    }

    private fun updateNotification() {
        notifier.update(downloads.value)
    }
}
