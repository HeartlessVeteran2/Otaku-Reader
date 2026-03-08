package app.otakureader.data.repository

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
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.DownloadStatus

private const val DOWNLOAD_CHANNEL_ID = "downloads_channel"
private const val DOWNLOAD_NOTIFICATION_ID = 2001

internal class DownloadNotifier(
    private val context: Context
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createChannel()
    }

    fun update(items: List<DownloadItem>) {
        if (items.isEmpty()) {
            notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
            return
        }

        val active = items.firstOrNull { it.isActive }
        if (active == null) {
            notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
            return
        }

        // Check for notification permission on Android 13+ before proceeding
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val completedCount = items.count { it.status == DownloadStatus.COMPLETED }

        val contentText = when (active?.status) {
            DownloadStatus.PAUSED -> "Paused • ${active.chapterTitle}"
            DownloadStatus.QUEUED -> "Queued • ${active.chapterTitle}"
            DownloadStatus.DOWNLOADING -> "Downloading • ${active.chapterTitle}"
            DownloadStatus.COMPLETED -> "Completed • ${active.chapterTitle}"
            else -> "Finishing downloads"
        }.let { base ->
            if (completedCount > 0) {
                "$base • $completedCount completed"
            } else {
                base
            }
        }

        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloads in progress")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        active?.let {
            builder.setProgress(100, it.progress.coerceIn(0, 100), false)
        }

        buildLaunchIntent()?.let { builder.setContentIntent(it) }

        // Permission is checked above for Android 13+, and not required for older versions
        @SuppressLint("MissingPermission")
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
    }

    fun cancel() {
        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
    }

    private fun buildLaunchIntent(): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null

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
                DOWNLOAD_CHANNEL_ID,
                "Chapter downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active download progress"
            }
            val systemManager = context.getSystemService(NotificationManager::class.java)
            systemManager?.createNotificationChannel(channel)
        }
    }

}
