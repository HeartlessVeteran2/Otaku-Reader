package app.otakureader.data.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages scheduling and cancellation of the daily [ReadingReminderWorker] via WorkManager.
 *
 * Call [schedule] when the user enables reading reminders or changes the reminder hour.
 * Call [cancel] when the user disables reading reminders.
 */
@Singleton
class ReadingReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Schedules (or reschedules) the daily reading reminder.
     *
     * Calculates an initial delay so the first notification fires at [hour]:00 today
     * (or tomorrow if that time has already passed), then repeats every 24 hours.
     *
     * @param hour Hour of the day (0–23) to send the reminder.
     */
    fun schedule(hour: Int) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelayMs = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<ReadingReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                ReadingReminderWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    /**
     * Cancels the daily reading reminder.
     */
    fun cancel() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(ReadingReminderWorker.WORK_NAME)
    }
}
