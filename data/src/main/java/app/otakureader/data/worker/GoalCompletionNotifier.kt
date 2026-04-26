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

/**
 * Sends a one-time "daily goal reached!" notification the first time the user
 * completes their daily chapter target.
 *
 * Call [checkAndNotify] after a chapter read is recorded. The notifier fires the
 * notification when today's chapter count equals the daily goal (i.e. the goal was
 * just met by this read, not already exceeded).
 */
@Singleton
class GoalCompletionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readingGoalPreferences: ReadingGoalPreferences,
    private val readingHistoryDao: ReadingHistoryDao,
) {

    /**
     * Checks whether the daily reading goal was just reached and, if so, fires a
     * congratulatory notification. The notification is sent only once — when
     * [chaptersToday] equals [dailyGoal] exactly — to avoid spamming the user for
     * every subsequent chapter read after the goal is met.
     */
    suspend fun checkAndNotify() {
        val dailyGoal = readingGoalPreferences.dailyChapterGoal.first()
        if (dailyGoal <= 0) return

        val zone = ZoneId.systemDefault()
        val startOfDayMs = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val chaptersToday = readingHistoryDao.getChaptersReadSince(startOfDayMs).first()

        // Fire only when the goal is hit exactly (transition from just-below to met).
        if (chaptersToday == dailyGoal) {
            showNotification(dailyGoal)
        }
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
