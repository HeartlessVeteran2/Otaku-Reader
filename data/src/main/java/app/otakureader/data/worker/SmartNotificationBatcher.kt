package app.otakureader.data.worker

import android.content.Context
import android.util.Log
import app.otakureader.core.preferences.NotificationPreferences
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Smart notification batcher that wraps [UpdateNotifier] with intelligent rate limiting,
 * quiet hours, and batching logic to prevent notification spam.
 *
 * **Key behaviors:**
 * - **Per-manga cooldown:** Won't notify for the same manga more than once per cooldown period
 * - **Max individual limit:** After N individual manga notifications, forces a summary-only digest
 * - **Quiet hours:** Respects configurable silent period (default 10 PM – 8 AM)
 * - **Smart grouping:** Groups by update recency; batches older updates into digest
 *
 * This class is stateless across process restarts (cooldowns stored in-memory only;
 * a process kill resets them, which is acceptable for notification cooldowns).
 */
class SmartNotificationBatcher(
    private val context: Context,
    private val notificationPreferences: NotificationPreferences,
    hideContent: Boolean = false,
    private val notifier: UpdateNotifier = UpdateNotifier(context, hideContent)
) {

    /** In-memory map of mangaId -> last notification timestamp (epoch millis). */
    private val lastNotificationMs = mutableMapOf<Long, Long>()

    /**
     * Send notifications with smart batching applied.
     *
     * @param mangaList         Manga with new chapters
     * @param totalNewChapters  Total chapters across all manga
     */
    suspend fun notify(mangaList: List<NotificationManga>, totalNewChapters: Int) {
        if (mangaList.isEmpty() || totalNewChapters <= 0) return

        val smartBatching = notificationPreferences.smartBatchingEnabled.first()
        if (!smartBatching) {
            // User disabled smart batching — send everything raw
            notifier.notify(mangaList, totalNewChapters)
            return
        }

        val cooldownMinutes = notificationPreferences.perMangaCooldownMinutes.first()
        val maxIndividual = notificationPreferences.maxIndividualNotifications.first()
        val respectQuiet = notificationPreferences.respectQuietHours.first()

        // Check quiet hours
        if (respectQuiet && isInQuietHours()) {
            Log.d(TAG, "In quiet hours — dropping ${mangaList.size} notifications")
            return
        }

        val now = System.currentTimeMillis()
        val cooldownMs = TimeUnit.MINUTES.toMillis(cooldownMinutes.toLong())

        // Filter out manga still in cooldown
        val eligible = mangaList.filter { manga ->
            val lastMs = lastNotificationMs[manga.id] ?: 0L
            val cooledDown = (now - lastMs) >= cooldownMs
            if (!cooledDown) {
                Log.d(TAG, "Skipping notification for '${manga.title}' — still in cooldown")
            }
            cooledDown
        }

        if (eligible.isEmpty()) {
            Log.d(TAG, "All ${mangaList.size} manga in cooldown — no notifications sent")
            return
        }

        // Record notification times for eligible manga
        eligible.forEach { lastNotificationMs[it.id] = now }

        // If we have too many individual notifications, batch into summary only
        if (eligible.size > maxIndividual) {
            Log.d(TAG, "${eligible.size} notifications exceed maxIndividual ($maxIndividual) — sending digest only")
            // Send only the summary notification (no individual ones)
            val totalEligibleChapters = eligible.sumOf { it.newChapterCount }
            notifier.notify(emptyList(), 0) // Clear any existing individuals first
            notifier.notifySummaryOnly(eligible.size, totalEligibleChapters)
        } else {
            // Send individual + summary as normal
            notifier.notify(eligible, eligible.sumOf { it.newChapterCount })
        }
    }

    /**
     * Show progress notification (pass-through, no batching applied).
     */
    fun showProgress(current: Int, total: Int, mangaTitle: String) {
        notifier.showProgress(current, total, mangaTitle)
    }

    /**
     * Cancel progress notification (pass-through).
     */
    fun cancelProgress() {
        notifier.cancelProgress()
    }

    /**
     * Check if current time falls within quiet hours.
     */
    private suspend fun isInQuietHours(): Boolean {
        val startHour = notificationPreferences.quietHoursStart.first()
        val endHour = notificationPreferences.quietHoursEnd.first()

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        return if (startHour < endHour) {
            // Same day window (e.g., 22 to 8 won't hit this branch)
            currentHour in startHour until endHour
        } else {
            // Overnight window (e.g., 22 to 8)
            currentHour >= startHour || currentHour < endHour
        }
    }

    companion object {
        private const val TAG = "SmartNotificationBatcher"
    }
}
