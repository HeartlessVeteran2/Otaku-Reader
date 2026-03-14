package app.otakureader.data.worker

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Handles notifications for sync operations.
 */
class SyncNotifier(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    /**
     * Show notification that sync is in progress.
     */
    @SuppressLint("MissingPermission")
    fun notifySyncing() {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Syncing library")
            .setContentText("Synchronizing your library with cloud storage...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SYNCING, notification)
    }

    /**
     * Show notification that sync completed successfully.
     *
     * @param changesCount Number of changes applied
     * @param message Optional success message
     */
    @SuppressLint("MissingPermission")
    fun notifySuccess(changesCount: Int, message: String?) {
        // Cancel syncing notification even if posting new notifications is not allowed.
        notificationManager.cancel(NOTIFICATION_ID_SYNCING)

        if (!hasNotificationPermission()) return

        val contentText = when {
            changesCount > 0 -> "Applied $changesCount changes"
            message != null -> message
            else -> "Library is up to date"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Sync complete")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_RESULT, notification)
    }

    /**
     * Show notification that sync failed.
     *
     * @param errorMessage Error message to display
     */
    @SuppressLint("MissingPermission")
    fun notifyFailure(errorMessage: String) {
        // Cancel syncing notification even if posting new notifications is not allowed.
        notificationManager.cancel(NOTIFICATION_ID_SYNCING)

        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Sync failed")
            .setContentText(errorMessage)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_RESULT, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Cloud sync notifications"
                setShowBadge(false)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        private const val CHANNEL_ID = "sync_channel"
        private const val NOTIFICATION_ID_SYNCING = 1001
        private const val NOTIFICATION_ID_RESULT = 1002
    }
}
