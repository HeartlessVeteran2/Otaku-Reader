@file:Suppress("MaxLineLength")
package app.otakureader.data.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import app.otakureader.core.database.dao.UpdateErrorDao
import app.otakureader.core.database.dao.UpdateRunSummaryDao
import app.otakureader.core.database.entity.UpdateErrorEntity
import app.otakureader.core.database.entity.UpdateRunSummaryEntity
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.data.download.ChapterDownloadRequest
import app.otakureader.data.download.DownloadManager
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.FeedItem
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.FeedRepository
import app.otakureader.domain.repository.SourceRepository
import java.time.Instant
import app.otakureader.domain.repository.downloadFolderNameFor
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.UpdateLibraryMangaUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

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
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val generalPreferences: GeneralPreferences,
    private val downloadManager: DownloadManager,
    private val chapterRepository: ChapterRepository,
    private val notificationPreferences: app.otakureader.core.preferences.NotificationPreferences,
    private val updateRunSummaryDao: UpdateRunSummaryDao,
    private val updateErrorDao: UpdateErrorDao,
    private val libraryUpdateFilter: LibraryUpdateFilter,
    private val sourceRepository: SourceRepository,
    private val feedRepository: FeedRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Refuse to run a second update alongside one already in flight.
        //
        // The manual and periodic flows use different WorkManager unique names (see [WORK_NAME] and
        // [PERIODIC_WORK_NAME]), so WorkManager will happily run both at once. Overlapping runs read
        // the stored chapter list before either writes, so both see the same chapters as new and
        // both insert — and they duplicate update notifications and download enqueues besides.
        //
        // Giving the two flows one unique name looks like the tidier fix and is not: a unique name
        // is shared between one-time and periodic work, so KEEP would make the manual refresh a
        // permanent no-op against the always-pending periodic chain, and REPLACE would cancel the
        // periodic schedule outright. Both worse than the overlap.
        //
        // tryLock rather than lock: if an update is already running, a second one has nothing to add,
        // and returning immediately is better than queueing a redundant pass behind it.
        if (!updateMutex.tryLock()) {
            Log.d(TAG, "Skipping library update - another update is already running")
            return Result.success()
        }
        try {
            return runUpdate()
        } finally {
            updateMutex.unlock()
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")
    private suspend fun runUpdate(): Result {
        val startTime = System.currentTimeMillis()
        return try {
            // Check if update should only run on Wi-Fi
            val updateOnlyOnWifi = libraryPreferences.updateOnlyOnWifi.first()
            if (updateOnlyOnWifi && !isConnectedToWifi()) {
                Log.d(TAG, "Skipping library update - not on Wi-Fi")
                return Result.retry()
            }

            // Get skip categories
            val skipCategoryIds = libraryPreferences.skipUpdateCategoryIds.first()
                .mapNotNull { it.toLongOrNull() }
                .toSet()

            // Get all library manga
            var libraryManga = getLibraryManga().first()

            // Filter out manga in skipped categories
            if (skipCategoryIds.isNotEmpty()) {
                libraryManga = libraryManga.filter { manga ->
                    manga.categoryIds.none { it in skipCategoryIds }
                }
            }

            // Apply smart-skip and per-category frequency filter via dedicated class.
            val now = System.currentTimeMillis()
            val filterResult = libraryUpdateFilter.apply(libraryManga, now)
            libraryManga = filterResult.filtered
            val skippedManga = filterResult.skipped
            val updatedCategoryIds = filterResult.updatedCategoryIds

            val notificationsEnabled = generalPreferences.notificationsEnabled.first()

            if (libraryManga.isEmpty()) {
                if (notificationsEnabled && skippedManga.isNotEmpty()) {
                    try { UpdateNotifier(applicationContext).showSkippedSummaryNotification(skippedManga.size) } catch (_: Exception) { }
                }
                return Result.success()
            }

            val autoDownloadEnabled = downloadPreferences.autoDownloadEnabled.first()
            val downloadOnlyOnWifi = downloadPreferences.downloadOnlyOnWifi.first()
            val autoDownloadLimit = downloadPreferences.autoDownloadLimit.first()
            val autoDownloadCategoryInclude = downloadPreferences.autoDownloadCategoryInclude.first()
            val autoDownloadCategoryExclude = downloadPreferences.autoDownloadCategoryExclude.first()
            val showUpdateProgress = libraryPreferences.showUpdateProgress.first()
            val hideNotificationContent = notificationPreferences.hideNotificationContent.first()

            // Check if Wi-Fi is available for downloads requiring Wi-Fi
            val onWifi = !downloadOnlyOnWifi || isConnectedToWifi()

            val mangaWithNewChapters = mutableListOf<NotificationManga>()
            val successfullyUpdatedCategoryIds = mutableSetOf<Long>()
            var failedUpdates = 0
            var processedCount = 0
            var newChapterTotal = 0
            val totalCount = libraryManga.size

            // Show progress notification if enabled
            val progressNotifier = if (showUpdateProgress) {
                UpdateNotifier(applicationContext, hideNotificationContent)
            } else null

            // Update each manga
            for (manga in libraryManga) {
                // Update progress notification
                if (showUpdateProgress) {
                    progressNotifier?.showProgress(
                        current = processedCount,
                        total = totalCount,
                        mangaTitle = manga.title
                    )
                }

                val result = updateLibraryManga(manga)

                result.onSuccess { newChapters ->
                    val newChapterCount = newChapters.size
                    // Clear any previously recorded failure now that this manga updated fine.
                    try {
                        updateErrorDao.deleteByMangaId(manga.id)
                    } catch (_: Exception) { }

                    newChapterTotal += newChapterCount
                    successfullyUpdatedCategoryIds.addAll(manga.categoryIds.filter { it in updatedCategoryIds })
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

                    // Record the arrivals in the feed. This is the only thing that has ever
                    // written a feed item: FeedRefreshWorker purges rows older than thirty days
                    // and nothing inserted one, so the tab showed an empty list permanently.
                    //
                    // Guarded rather than left to throw. A feed row is a nicety; failing the whole
                    // library update — and losing the auto-download pass below with it — because a
                    // secondary write failed would trade a real feature for a cosmetic one.
                    if (newChapterCount > 0) {
                        try {
                            recordFeedItems(manga, newChapters)
                        } catch (e: CancellationException) {
                            // Not an ordinary failure. runCatching swallowed this, which let a
                            // cancelled update carry on into the auto-download pass below instead
                            // of stopping.
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not record feed items for ${manga.id}", e)
                        }
                    }

                    // Auto-download new chapters if conditions are met.
                    // Per-manga autoDownload can opt-in even when global is off,
                    // but cannot opt-out when global is on.
                    if (newChapterCount > 0 && onWifi) {
                        val shouldDownloadForManga = manga.autoDownload || autoDownloadEnabled

                        if (shouldDownloadForManga) {
                            // Category-level auto-download filter.
                            // manga.categoryIds comes from the domain model that is already
                            // populated by GetLibraryMangaUseCase (it includes category joins).
                            val mangaCategoryIds = manga.categoryIds

                            // Include list: if non-empty, manga must belong to at least one
                            // listed category. An empty include list means "all categories".
                            val passesInclude = autoDownloadCategoryInclude.isEmpty() ||
                                mangaCategoryIds.any { it in autoDownloadCategoryInclude }

                            // Exclude list: if the manga belongs to ANY excluded category,
                            // skip it regardless of the include list.
                            val passesExclude = mangaCategoryIds.none { it in autoDownloadCategoryExclude }

                            if (passesInclude && passesExclude) {
                                enqueueAutoDownloads(manga.id, manga.sourceId, manga.title, autoDownloadLimit)
                            }
                        }
                    }
                }.onFailure { throwable ->
                    failedUpdates++
                    try {
                        updateErrorDao.upsert(
                            UpdateErrorEntity(
                                mangaId = manga.id,
                                errorMessage = throwable.message ?: throwable::class.simpleName ?: "Unknown error",
                                timestamp = System.currentTimeMillis(),
                            )
                        )
                    } catch (_: Exception) { }
                }

                processedCount++
            }

            // Cancel progress notification
            if (showUpdateProgress) {
                progressNotifier?.cancelProgress()
            }

            // Persist per-category last-update timestamps only for categories where
            // at least one manga was successfully fetched (failures should not advance the clock).
            if (successfullyUpdatedCategoryIds.isNotEmpty()) {
                val updated = libraryPreferences.categoryLastUpdateMs.first().toMutableMap()
                successfullyUpdatedCategoryIds.forEach { catId -> updated[catId] = now }
                libraryPreferences.setCategoryLastUpdateMs(updated)
            }

            // Send skipped-summary notification if any manga were skipped
            if (notificationsEnabled && skippedManga.isNotEmpty()) {
                try {
                    UpdateNotifier(applicationContext).showSkippedSummaryNotification(skippedManga.size)
                } catch (_: Exception) { }
            }

            // Send notification if new chapters were found and notifications are enabled
            if (notificationsEnabled && mangaWithNewChapters.isNotEmpty()) {
                val totalNewChapters = mangaWithNewChapters.sumOf { it.newChapterCount }
                try {
                    SmartNotificationBatcher(
                        context = applicationContext,
                        notificationPreferences = notificationPreferences,
                        hideContent = hideNotificationContent,
                    ).notify(mangaWithNewChapters, totalNewChapters)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Notification failures should not fail the entire library update.
                    Log.w(TAG, "Failed to send library update notification", e)
                }
            }

            // Persist a diagnostics summary for the Updates screen (#1041).
            try {
                updateRunSummaryDao.insert(
                    app.otakureader.core.database.entity.UpdateRunSummaryEntity(
                        timestamp = System.currentTimeMillis(),
                        checkedCount = totalCount,
                        newChaptersCount = newChapterTotal,
                        skippedCount = skippedManga.size,
                        failedCount = failedUpdates,
                        durationMs = System.currentTimeMillis() - startTime,
                    )
                )
                // Keep only 90 days of history to prevent unbounded growth.
                updateRunSummaryDao.deleteOlderThan(
                    System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(90)
                )
            } catch (_: Exception) {
                // Diagnostics failure must never fail the worker itself.
            }

            // Consider it a success if at least some manga were updated successfully
            if (failedUpdates == libraryManga.size) {
                Result.failure()
            } else {
                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
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

            val sourceName = downloadFolderNameFor(sourceId)

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Don't fail the whole library update over a download enqueue problem,
            // but leave a trace — a silent catch here hides broken auto-download.
            Log.e(TAG, "Failed to enqueue auto-downloads for manga $mangaId", e)
        }
    }

    private fun isConnectedToWifi(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Turns freshly-arrived chapters into feed rows.
     *
     * The source *name* is looked up rather than derived from the id, because the feed shows it to
     * the user and a numeric id is not a name. A source that is no longer installed yields null —
     * an extension can be uninstalled while its manga stay in the library — so the id is shown
     * instead of dropping the row: the chapter genuinely arrived, and hiding it because its
     * extension went away would be a worse answer than an unfamiliar label.
     *
     * `dateUpload` is the timestamp, falling back to now. The feed is ordered by it, and a source
     * that reports 0 for an undated chapter would otherwise pin that row to 1970 and bury it.
     */
    private suspend fun recordFeedItems(manga: Manga, newChapters: List<Chapter>) {
        val sourceName = sourceRepository.getSourceByKey(manga.sourceId)?.name
            ?: manga.sourceId.toString()
        val now = Instant.now()
        feedRepository.addFeedItems(
            newChapters.map { chapter ->
                FeedItem(
                    mangaId = manga.id,
                    mangaTitle = manga.title,
                    mangaThumbnailUrl = manga.thumbnailUrl,
                    chapterId = chapter.id,
                    chapterName = chapter.name,
                    chapterNumber = chapter.chapterNumber,
                    sourceId = manga.sourceId,
                    sourceName = sourceName,
                    timestamp = chapter.dateUpload.takeIf { it > 0 }
                        ?.let(Instant::ofEpochMilli)
                        ?: now,
                )
            }
        )
    }

    companion object {
        private const val TAG = "LibraryUpdateWorker"

        /**
         * Serialises update runs across both work names.
         *
         * Process-wide because WorkManager runs both flows in the app process; if this app ever
         * moves WorkManager to a second process, this guard stops holding and the overlap returns.
         */
        private val updateMutex = Mutex()

        const val WORK_NAME = "library_update"
        const val PERIODIC_WORK_NAME = "library_update_periodic"

        /**
         * Enqueues a one-time library update work request.
         * This can be called from MainActivity for auto-refresh on start.
         *
         * @param context Application context
         */
        fun enqueue(context: Context) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<LibraryUpdateWorker>()
                .build()

            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, androidx.work.ExistingWorkPolicy.KEEP, workRequest)
        }

        /**
         * Schedules periodic library updates.
         *
         * @param context Application context
         * @param intervalHours Update interval in hours (app minimum is 1 hour for battery/network efficiency, stricter than WorkManager's 15-minute periodic minimum)
         * @param wifiOnly Whether to run only on unmetered (Wi-Fi) network
         * @param requireCharging Whether to run only while the device is charging
         */
        fun schedule(
            context: Context,
            intervalHours: Int = 12,
            wifiOnly: Boolean = false,
            requireCharging: Boolean = false
        ) {
            val safeIntervalHours = intervalHours.coerceAtLeast(1)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(requireCharging)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(
                repeatInterval = safeIntervalHours.toLong(),
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        /**
         * Cancels periodic library updates.
         */
        fun cancelPeriodic(context: Context) {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
