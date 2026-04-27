package app.otakureader.domain.history

/**
 * Schedules durable persistence of reading history on reader exit.
 *
 * Implementations must survive process death (e.g. via WorkManager) so that
 * history is recorded even when the OS kills the app before a coroutine completes.
 */
interface ReadingHistoryScheduler {
    /**
     * Schedule a history-write for the chapter that was just closed.
     *
     * Safe to call from [androidx.lifecycle.ViewModel.onCleared] — the scheduled
     * work must outlive the ViewModel's coroutine scope.
     */
    fun scheduleExit(
        chapterId: Long,
        readAt: Long,
        durationMs: Long,
        isIncognito: Boolean,
        lastPageRead: Int,
        isRead: Boolean,
    )
}
