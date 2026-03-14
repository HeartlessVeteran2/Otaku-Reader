package app.otakureader.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.otakureader.core.preferences.SyncPreferences
import app.otakureader.domain.sync.SyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background worker that performs automatic cloud sync.
 *
 * This worker:
 * - Checks if sync is enabled and configured
 * - Respects Wi-Fi only preference
 * - Performs bidirectional sync (upload + download + merge)
 * - Posts notifications on success/failure (via SyncNotifier)
 * - Scheduled based on user's sync interval preference
 *
 * The worker uses WorkManager's constraints to ensure sync only happens
 * when network conditions are met.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager,
    private val syncPreferences: SyncPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val notifier = SyncNotifier(applicationContext)

        // Check if sync is enabled and properly configured for automatic sync
        val isSyncEnabled = syncPreferences.isSyncEnabled.first()
        val isAutoSyncEnabled = syncPreferences.autoSyncEnabled.first()
        val providerId = syncPreferences.providerId.first()
        if (!isSyncEnabled || !isAutoSyncEnabled || providerId == null) {
                    cancel(applicationContext)
                    return Result.success()
                }

        notifier.notifySyncing()

        return syncManager.sync().fold(
            onSuccess = { syncResult ->
                notifier.notifySuccess(
                    changesCount = syncResult.totalChanges,
                    message = syncResult.message
                )
                Result.success()
            },
            onFailure = { throwable ->
                if (throwable is CancellationException) {
                    // Propagate coroutine/WorkManager cancellation without showing a failure notification.
                    throw throwable
                }
                notifier.notifyFailure(throwable.message ?: "Unknown error")
                if (throwable is IllegalStateException || throwable is NotImplementedError) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        )
    }

    companion object {
        const val WORK_NAME = "auto_sync"

        /**
         * Schedule periodic sync based on user preferences.
         *
         * @param context Application context
         * @param intervalHours Sync interval in hours
         * @param wifiOnly Whether to sync only on Wi-Fi
         */
        fun schedule(
            context: Context,
            intervalHours: Int = 24,
            wifiOnly: Boolean = true
        ) {
            // Ensure we never pass an invalid repeat interval to WorkManager.
            // If callers provide 0 or a negative value, fall back to the minimum of 1 hour.
            val safeIntervalHours = intervalHours.coerceAtLeast(1)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = safeIntervalHours.toLong(),
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        /**
         * Cancel scheduled sync.
         *
         * @param context Application context
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
