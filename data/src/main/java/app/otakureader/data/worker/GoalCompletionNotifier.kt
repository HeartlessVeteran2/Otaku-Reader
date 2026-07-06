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
import app.otakureader.domain.model.AchievementDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

internal const val GOAL_CHANNEL_ID = "reading_goal_channel"
private const val GOAL_NOTIFICATION_ID = 4002
private const val ACHIEVEMENT_NOTIFICATION_ID = 4003

/**
 * Sends a one-time "daily goal reached!" notification the first time the user
 * completes their daily chapter target.
 *
 * Call [checkAndNotify] after a chapter read is recorded. The notifier fires the
 * notification when today's chapter count equals the daily goal and the goal has
 * not already been achieved today. Last-notified date is stored via
 * [ReadingGoalPreferences] DataStore instead of raw SharedPreferences.
 */
@Singleton
class GoalCompletionNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
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
        if (chaptersToday == dailyGoal && readingGoalPreferences.getLastGoalNotifiedDate() != today.toString()) {
            showNotification(dailyGoal)
            readingGoalPreferences.setLastGoalNotifiedDate(today.toString())
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
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(GOAL_NOTIFICATION_ID, notification)
    }

    /**
     * Posts a notification when an achievement is newly unlocked.
     * Uses ID 4003 so it does not collide with the daily goal notification (4002).
     */
    @SuppressLint("MissingPermission")
    fun notifyAchievementUnlocked(def: AchievementDefinition) {
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
            ACHIEVEMENT_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val friendlyName = def.name
            .lowercase()
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase() }

        val notification = NotificationCompat.Builder(context, GOAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Achievement Unlocked!")
            .setContentText(friendlyName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(ACHIEVEMENT_NOTIFICATION_ID, notification)
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
