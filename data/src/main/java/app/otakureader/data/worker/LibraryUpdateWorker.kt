package app.otakureader.data.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.data.download.ChapterDownloadRequest
import app.otakureader.data.download.DownloadManager
import app.otakureader.domain.repository.ChapterRepository
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
    private val generalPreferences: GeneralPreferences,
    private val downloadManager: DownloadManager,
    private val chapterRepository: ChapterRepository
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
            val notificationsEnabled = generalPreferences.notificationsEnabled.first()

            // Check if Wi-Fi is available for downloads requiring Wi-Fi
            val onWifi = !downloadOnlyOnWifi || isConnectedToWifi()

            val mangaWithNewChapters = mutableListOf<NotificationManga>()
            var failedUpdates = 0

            // Update each manga
            for (manga in libraryManga) {
                val result = updateLibraryManga(manga)

                result.onSuccess { newChapterCount ->
                    if (newChapterCount > 0) {
                        // Only add to notification list if notifications enabled for this manga
                        if (manga.notifyNewChapters) {
                            mangaWithNewChapters.add(
                                NotificationManga(
                                    id = manga.id,
                                    title = manga.title,
                                    coverUrl = manga.thumbnailUrl,
                                    newChapterCount = newChapterCount
                                )
                            )
                        }
                    }

                    // Auto-download new chapters if conditions are met.
                    // Per-manga autoDownload can opt-in even when global is off,
                    // but cannot opt-out when global is on.
                    if (newChapterCount > 0 && onWifi) {
                        val shouldDownloadForManga = manga.autoDownload || autoDownloadEnabled

                        if (shouldDownloadForManga) {
                            enqueueAutoDownloads(manga.id, manga.sourceId, manga.title, autoDownloadLimit)
                        }
                    }
                }.onFailure {
                    failedUpdates++
                }
            }

            // Send notification if new chapters were found and notifications are enabled
            if (notificationsEnabled && mangaWithNewChapters.isNotEmpty()) {
                val totalNewChapters = mangaWithNewChapters.sumOf { it.newChapterCount }
                try {
                    UpdateNotifier(applicationContext).notify(
                        mangaWithNewChapters,
                        totalNewChapters
                    )
                } catch (e: Exception) {
                    // Notification failures should not fail the entire library update.
                    // Log the error for diagnostics (e.g., SecurityException, channel creation issues).
                    Log.w(TAG, "Failed to send library update notification", e)
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

    private suspend fun enqueueAutoDownloads(mangaId: Long, sourceId: Long, mangaTitle: String, limit: Int) {
        try {
            // Ensure limit is at least 1 to avoid IllegalArgumentException in take(limit)
            val safeLimit = limit.coerceAtLeast(1)

            // Get unread chapters for this manga, limited by the auto-download limit
            val chapters = chapterRepository.getChaptersByMangaId(mangaId).first()
                .filter { !it.read }
                .sortedByDescending { it.chapterNumber }
                .take(safeLimit)

            // Use sourceId as a stable directory key (same as in DetailsViewModel)
            val sourceName = sourceId.toString()

            for (chapter in chapters) {
                // Enqueue with empty pageUrls - DownloadManager will handle fetching them later
                val request = ChapterDownloadRequest(
                    mangaId = mangaId,
                    chapterId = chapter.id,
                    sourceName = sourceName,
                    mangaTitle = mangaTitle,
                    chapterTitle = chapter.name,
                    pageUrls = emptyList() // Pages will be fetched when download actually starts
                )
                downloadManager.enqueue(request)
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
        private const val TAG = "LibraryUpdateWorker"
        const val WORK_NAME = "library_update"
    }
}
