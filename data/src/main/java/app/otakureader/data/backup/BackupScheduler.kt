package app.otakureader.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.otakureader.data.worker.BackupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages scheduling and cancellation of the periodic [BackupWorker] via WorkManager.
 *
 * Call [schedule] whenever the user enables automatic backups or changes the interval.
 * Call [cancel] when the user disables automatic backups.
 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Schedules (or reschedules) the periodic backup job.
     *
     * Uses [ExistingPeriodicWorkPolicy.UPDATE] so that changing the interval takes effect
     * immediately without waiting for the currently-scheduled run to complete.
     *
     * @param intervalHours How often to run the backup (in hours).
     *   Product minimum is 1 hour; WorkManager's own minimum is 15 minutes.
     */
    fun schedule(intervalHours: Int) {
        val safeInterval = intervalHours.coerceAtLeast(1).toLong()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(safeInterval, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                BackupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    /**
     * Cancels the periodic backup job.
     */
    fun cancel() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(BackupWorker.WORK_NAME)
    }
}
