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
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

const val UPDATE_CHANNEL_ID = "library_updates_channel"
const val GROUP_KEY_UPDATES = "library_updates"
const val UPDATE_NOTIFICATION_TAG = "library_update"
const val SUMMARY_NOTIFICATION_ID = Int.MAX_VALUE

private const val EXTRA_MANGA_ID = "mangaId"
private const val EXTRA_DESTINATION = "destination"
private const val DESTINATION_UPDATES = "updates"

/**
 * Data class for notification manga info.
 */
data class NotificationManga(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val newChapterCount: Int
)

/**
 * Enhanced helper class for sending notifications when new chapters are found.
 * Supports manga covers, action buttons, and grouped notifications.
 */
class UpdateNotifier(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createChannel()
    }

    /**
     * Show a summary notification for newly discovered chapters with individual manga notifications.
     *
     * @param mangaList       List of manga with new chapters
     * @param totalNewChapters Total number of new chapters found across all manga
     */
    @SuppressLint("MissingPermission")
    suspend fun notify(mangaList: List<NotificationManga>, totalNewChapters: Int) {
        if (totalNewChapters <= 0 || mangaList.isEmpty()) return

        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        // Build individual notifications for each manga
        mangaList.forEach { manga ->
            val notification = buildMangaNotification(manga)
            notificationManager.notify(UPDATE_NOTIFICATION_TAG, manga.id.hashCode(), notification)
        }

        // Build and show summary notification
        val summaryNotification = buildSummaryNotification(mangaList.size, totalNewChapters)
        notificationManager.notify(UPDATE_NOTIFICATION_TAG, SUMMARY_NOTIFICATION_ID, summaryNotification)
    }

    private suspend fun buildMangaNotification(manga: NotificationManga): android.app.Notification {
        val contentText = when {
            manga.newChapterCount == 1 -> "1 new chapter"
            else -> "${manga.newChapterCount} new chapters"
        }

        // Load cover image if available
        val coverBitmap = manga.coverUrl?.let { url ->
            loadCoverImage(url)
        }

        // Build action intent to open manga (always non-null, falls back to CATEGORY_LAUNCHER)
        val openIntent = buildOpenMangaIntent(manga.id)

        return NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(manga.title)
            .setContentText(contentText)
            .setLargeIcon(coverBitmap)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_UPDATES)
            .setContentIntent(openIntent)
            .build()
    }

    private fun buildSummaryNotification(mangaCount: Int, totalNewChapters: Int): android.app.Notification {
        val contentText = when {
            mangaCount == 1 -> "$totalNewChapters new chapter${if (totalNewChapters > 1) "s" else ""} available"
            else -> "$totalNewChapters new chapters in $mangaCount manga"
        }

        return NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Library Updated")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_UPDATES)
            .setGroupSummary(true)
            .setContentIntent(buildLaunchIntent())
            .build()
    }

    private suspend fun loadCoverImage(url: String): android.graphics.Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                // Add timeout to prevent notifications from being delayed by slow image loads
                withTimeout(5000L) { // 5 second timeout
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .size(256, 256)
                        .build()

                    val result = context.imageLoader.execute(request)
                    result.image?.toBitmap()
                }
            } catch (e: Exception) {
                // Catches both timeout exceptions and image loading failures
                null
            }
        }
    }

    private fun buildOpenMangaIntent(mangaId: Long): PendingIntent {
        val launchIntent = createLauncherIntent().apply {
            putExtra(EXTRA_MANGA_ID, mangaId)
            putExtra(EXTRA_DESTINATION, DESTINATION_UPDATES)
        }

        return PendingIntent.getActivity(
            context,
            mangaId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildLaunchIntent(): PendingIntent {
        return PendingIntent.getActivity(
            context,
            0,
            createLauncherIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Returns a launcher [Intent] for this package. Prefers [PackageManager.getLaunchIntentForPackage]
     * and falls back to ACTION_MAIN/CATEGORY_LAUNCHER when that call returns null (e.g. on some
     * OEM ROMs or work-profile setups where the method is unreliable).
     */
    private fun createLauncherIntent(): Intent {
        return context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(context.packageName)
            }
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
