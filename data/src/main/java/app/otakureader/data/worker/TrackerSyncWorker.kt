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
import app.otakureader.domain.repository.TrackerSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background worker that performs periodic 2-way sync between the local library
 * and all configured external trackers.
 *
 * Processes every entry in PENDING state and pushes/pulls changes according to
 * the per-tracker [ConflictResolution] strategy. Entries in CONFLICT state that
 * require user input ([ConflictResolution.ASK]) are left for the user to resolve
 * via the Tracking screen.
 *
 * Schedule via [TrackerSyncWorker.schedule]; cancel via [TrackerSyncWorker.cancel].
 */
@HiltWorker
class TrackerSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val trackerSyncRepository: TrackerSyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val summary = trackerSyncRepository.syncAllPending()
            // Retry if any hard failures occurred; conflicts are handled by the user
            if (summary.failed > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "tracker_2way_sync"

        /**
         * Schedules (or reschedules) the periodic tracker sync job.
         *
         * Requires a network connection. Runs every [intervalHours] hours.
         * Uses [ExistingPeriodicWorkPolicy.UPDATE] so that rescheduling takes effect
         * without waiting for the current period to expire.
         *
         * @param context Application context.
         * @param intervalHours How often to sync (in hours, minimum 1).
         */
        fun schedule(context: Context, intervalHours: Int = 1) {
            val safeInterval = intervalHours.coerceAtLeast(1)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<TrackerSyncWorker>(
                repeatInterval = safeInterval.toLong(),
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
         * Cancels the scheduled tracker sync job.
         *
         * @param context Application context.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
