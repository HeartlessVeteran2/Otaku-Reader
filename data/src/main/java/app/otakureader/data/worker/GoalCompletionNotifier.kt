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
import app.otakureader.core.database.dao.ReadingHistoryDao
import app.otakureader.core.preferences.ReadingGoalPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

internal const val GOAL_CHANNEL_ID = "reading_goal_channel"
private const val GOAL_NOTIFICATION_ID = 4002
private const val PREFS_NAME = "goal_completion_notifier"
private const val KEY_LAST_NOTIFIED_DATE = "last_notified_date"

/**
 * Sends a one-time "daily goal reached!" notification the first time the user
 * completes their daily chapter target.
 *
 * Call [checkAndNotify] after a chapter read is recorded. The notifier fires the
 * notification when today's chapter count equals the daily goal and the goal has
 * not already been achieved today (tracked via persistent SharedPreferences).
 */
@Singleton
class GoalCompletionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readingGoalPreferences: ReadingGoalPreferences,
    private val readingHistoryDao: ReadingHistoryDao,
) {

    /**
     * Checks whether the daily reading goal was just reached and, if so, fires a
     * congratulatory notification. The notification is sent only once per day —
     * when [chaptersToday] equals [dailyGoal] exactly and the goal has not already
     * been notified today.
     */
    suspend fun checkAndNotify() {
        val dailyGoal = readingGoalPreferences.dailyChapterGoal.first()
        if (dailyGoal <= 0) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfDayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val chaptersToday = readingHistoryDao.getChaptersReadSince(startOfDayMs).first()

        // Fire only when the goal is hit exactly (transition from just-below to met)
        // and we haven't already notified today.
        if (chaptersToday == dailyGoal && !wasNotifiedToday(today)) {
            showNotification(dailyGoal)
            markNotifiedToday(today)
        }
    }

    private fun wasNotifiedToday(today: LocalDate): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_DATE, null)
        return lastNotified == today.toString()
    }

    private fun markNotifiedToday(today: LocalDate) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_NOTIFIED_DATE, today.toString()).apply()
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(dailyGoal: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        createChannel()

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(context.packageName)
            }

        val pendingIntent = PendingIntent.getActivity(
            context,
            GOAL_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GOAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Daily goal reached! 🎉")
            .setContentText("You've read $dailyGoal chapter(s) today. Great work!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(GOAL_NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GOAL_CHANNEL_ID,
                "Reading goal",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notification when you reach your daily reading goal"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
