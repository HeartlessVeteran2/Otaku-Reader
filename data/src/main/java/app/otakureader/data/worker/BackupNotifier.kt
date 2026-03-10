package app.otakureader.data.worker

import android.Manifest
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

internal const val BACKUP_CHANNEL_ID = "backup_channel"
internal const val BACKUP_NOTIFICATION_ID = 3001

/**
 * Helper class for posting notifications when automatic backups succeed or fail.
 */
internal class BackupNotifier(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createChannel()
    }

    /** Show a success notification with the file name of the backup created. */
    fun notifySuccess(fileName: String) {
        showNotification(
            title = "Backup completed",
            text = "Saved as $fileName"
        )
    }

    /** Show a failure notification with a short description of the error. */
    fun notifyFailure(message: String?) {
        showNotification(
            title = "Backup failed",
            text = message ?: "An unknown error occurred"
        )
    }

    private fun showNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val notification = NotificationCompat.Builder(context, BACKUP_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildLaunchIntent())
            .build()

        @Suppress("MissingPermission")
        notificationManager.notify(BACKUP_NOTIFICATION_ID, notification)
    }

    private fun buildLaunchIntent(): PendingIntent {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(context.packageName)
            }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BACKUP_CHANNEL_ID,
                "Automatic backups",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when automatic backups complete or fail"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
