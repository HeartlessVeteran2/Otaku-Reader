package app.otakureader.data.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.data.download.ChapterDownloadRequest
import app.otakureader.data.download.DownloadManager
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.UpdateLibraryMangaUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Background worker that checks for new chapters in the library.
 * This worker fetches the latest chapters for all favorite manga and updates the database.
 * If auto-download is enabled, it will also enqueue downloads for new chapters.
 */
@HiltWorker
class LibraryUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val getLibraryManga: GetLibraryMangaUseCase,
    private val updateLibraryManga: UpdateLibraryMangaUseCase,
    private val downloadPreferences: DownloadPreferences,
    private val downloadManager: DownloadManager,
    private val chapterRepository: ChapterRepository,
    private val sourceRepository: SourceRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Get all library manga
            val libraryManga = getLibraryManga().first()

            if (libraryManga.isEmpty()) {
                return Result.success()
            }

            val autoDownloadEnabled = downloadPreferences.autoDownloadEnabled.first()
            val downloadOnlyOnWifi = downloadPreferences.downloadOnlyOnWifi.first()
            val autoDownloadLimit = downloadPreferences.autoDownloadLimit.first()

            // Check if we should skip auto-download due to Wi-Fi requirement
            val shouldAutoDownload = autoDownloadEnabled &&
                (!downloadOnlyOnWifi || isConnectedToWifi())

            var totalNewChapters = 0
            var failedUpdates = 0

            // Update each manga
            for (manga in libraryManga) {
                val result = updateLibraryManga(manga)

                result.onSuccess { newChapterCount ->
                    totalNewChapters += newChapterCount

                    // Auto-download new chapters if enabled and conditions are met
                    if (shouldAutoDownload && newChapterCount > 0) {
                        // Check if auto-download is enabled for this specific manga (per-manga override)
                        val mangaAutoDownloadEnabled = manga.autoDownload ||
                            (autoDownloadEnabled && !manga.autoDownload)

                        if (mangaAutoDownloadEnabled || (!manga.autoDownload && autoDownloadEnabled)) {
                            enqueueAutoDownloads(manga.id, autoDownloadLimit)
                        }
                    }
                }.onFailure {
                    failedUpdates++
                }
            }

            // Consider it a success if at least some manga were updated successfully
            if (failedUpdates == libraryManga.size) {
                Result.failure()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun enqueueAutoDownloads(mangaId: Long, limit: Int) {
        try {
            // Get unread chapters for this manga, limited by the auto-download limit
            val chapters = chapterRepository.getChaptersByMangaId(mangaId).first()
                .filter { !it.read }
                .sortedByDescending { it.chapterNumber }
                .take(limit)

            // Get manga details for download request
            val manga = getLibraryManga().first().firstOrNull { it.id == mangaId } ?: return

            // Get source name from source ID
            val source = sourceRepository.getSourceById(manga.sourceId) ?: return

            for (chapter in chapters) {
                // Fetch page URLs for the chapter
                val pageUrls = try {
                    sourceRepository.getPageList(source, chapter).getOrNull() ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                if (pageUrls.isNotEmpty()) {
                    val request = ChapterDownloadRequest(
                        mangaId = manga.id,
                        chapterId = chapter.id,
                        sourceName = source.name,
                        mangaTitle = manga.title,
                        chapterTitle = chapter.name,
                        pageUrls = pageUrls
                    )
                    downloadManager.enqueue(request)
                }
            }
        } catch (e: Exception) {
            // Log error but don't fail the entire worker
        }
    }

    private fun isConnectedToWifi(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        const val WORK_NAME = "library_update"
    }
}
