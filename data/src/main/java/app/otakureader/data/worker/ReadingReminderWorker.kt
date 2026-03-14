package app.otakureader.data.worker

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.preferences.ReadingGoalPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

internal const val REMINDER_CHANNEL_ID = "reading_reminder_channel"
private const val REMINDER_NOTIFICATION_ID = 4001

/**
 * Background worker that sends a daily reading reminder notification.
 *
 * If the user has a daily reading goal and has not yet met it, the notification
 * encourages them to keep reading. If no goal is set, a generic reminder is shown.
 */
@HiltWorker
class ReadingReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val readingGoalPreferences: ReadingGoalPreferences,
    private val readingHistoryDao: ReadingHistoryDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val remindersEnabled = readingGoalPreferences.remindersEnabled.first()
            if (!remindersEnabled) return Result.success()

            val dailyGoal = readingGoalPreferences.dailyChapterGoal.first()
            val zone = ZoneId.systemDefault()
            val startOfDayMs = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val chaptersToday = readingHistoryDao.getChaptersReadSince(startOfDayMs).first()

            // Don't remind if daily goal is set and already met
            if (dailyGoal > 0 && chaptersToday >= dailyGoal) return Result.success()

            showNotification(dailyGoal, chaptersToday)
            Result.success()
        } catch (e: Exception) {
            Result.success() // Non-critical; don't retry
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(dailyGoal: Int, chaptersToday: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        createChannel()

        val contentText = when {
            dailyGoal > 0 && chaptersToday > 0 ->
                "You've read $chaptersToday/$dailyGoal chapters today. Keep going!"
            dailyGoal > 0 ->
                "Your goal: $dailyGoal chapters today. Start reading!"
            else ->
                "Don't break your reading streak! Open a manga now."
        }

        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(applicationContext.packageName)
            }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            REMINDER_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Time to read!")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(REMINDER_NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Reading reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reading reminder notifications"
            }
            applicationContext.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val WORK_NAME = "reading_reminder"
    }
}
