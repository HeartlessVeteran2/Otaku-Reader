package app.otakureader.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.otakureader.core.database.dao.FeedDao
import app.otakureader.domain.repository.FeedRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically refreshes the feed by cleaning up old items.
 * Sources are resolved at runtime from the [FeedRepository]; new chapter items
 * would be inserted by the source extension loader once implemented.
 */
@HiltWorker
class FeedRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val feedRepository: FeedRepository,
    private val feedDao: FeedDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val sources = feedRepository.getFeedSources().first()
            val enabledSources = sources.filter { it.isEnabled }

            if (enabledSources.isEmpty()) {
                return Result.success()
            }

            // Purge items older than 30 days to keep the feed from growing unbounded.
            val cutoff = Instant.now().minusSeconds(FEED_RETENTION_SECONDS)
            feedDao.clearOldFeedItems(cutoff)

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Feed refresh worker failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "FeedRefreshWorker"
        const val WORK_NAME = "feed_refresh"
        private const val FEED_RETENTION_SECONDS = 30L * 24 * 60 * 60 // 30 days

        /**
         * Schedules a periodic feed refresh every 6 hours.
         * Uses [ExistingPeriodicWorkPolicy.KEEP] so rescheduling doesn't reset the timer.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /**
         * Enqueues a one-time immediate feed refresh.
         */
        fun enqueueOneTime(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<FeedRefreshWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_once",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}
