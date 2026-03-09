package app.otakureader.data.worker

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal const val UPDATE_CHANNEL_ID = "library_updates_channel"
internal const val UPDATE_NOTIFICATION_ID = 2002

/**
 * Helper class for sending notifications when new chapters are found during library updates.
 */
internal class UpdateNotifier(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createChannel()
    }

    /**
     * Show a summary notification for newly discovered chapters.
     *
     * @param mangaCount      Number of manga titles that received new chapters.
     * @param totalNewChapters Total number of new chapters found across all manga.
     */
    @SuppressLint("MissingPermission")
    fun notify(mangaCount: Int, totalNewChapters: Int) {
        if (totalNewChapters <= 0) return

        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val contentText = when {
            mangaCount == 1 ->
                "$totalNewChapters new chapter${if (totalNewChapters > 1) "s" else ""} available"
            else ->
                "$totalNewChapters new chapters in $mangaCount manga"
        }

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Library Updated")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { buildLaunchIntent()?.let { setContentIntent(it) } }
            .build()

        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
    }

    private fun buildLaunchIntent(): PendingIntent? {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "Library updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when new chapters are found in your library"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
