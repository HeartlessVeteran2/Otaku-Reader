package app.otakureader.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.otakureader.domain.repository.ChapterRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-shot WorkManager task that persists a reading-session history record and
 * optionally updates chapter-progress when the reader is closed.
 *
 * ## Why WorkManager? (Audit H-5)
 * The previous implementation used a custom `cleanupScope` coroutine launched from
 * `ViewModel.onCleared()`. That coroutine survives `viewModelScope` cancellation
 * but is killed if the OS terminates the process due to low memory while the app is
 * in the background. Using a `WorkManager` one-shot request guarantees the write is
 * eventually completed — WorkManager will restart the task after the process is
 * recreated if it did not finish before the process died.
 */
@HiltWorker
class RecordReadingHistoryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val chapterRepository: ChapterRepository,
    private val goalCompletionNotifier: GoalCompletionNotifier,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val chapterId = inputData.getLong(KEY_CHAPTER_ID, INVALID_ID)
        val readAt = inputData.getLong(KEY_READ_AT, INVALID_ID)
        val durationMs = inputData.getLong(KEY_DURATION_MS, 0L)
        val isIncognito = inputData.getBoolean(KEY_IS_INCOGNITO, false)

        if (chapterId == INVALID_ID || readAt == INVALID_ID) {
            // Missing required data — nothing to save, fail permanently (no retry).
            return Result.failure(
                workDataOf("error" to "Missing required input data (chapterId or readAt)")
            )
        }

        // Respect incognito mode — never write history or progress in that case.
        if (isIncognito) return Result.success()

        return try {
            chapterRepository.recordHistory(
                chapterId = chapterId,
                readAt = readAt,
                readDurationMs = durationMs,
            )
            // Optionally update chapter progress if the caller supplied progress data.
            val lastPageRead = inputData.getInt(KEY_LAST_PAGE_READ, INVALID_PAGE)
            if (lastPageRead != INVALID_PAGE) {
                val isRead = inputData.getBoolean(KEY_IS_READ, false)
                chapterRepository.updateChapterProgress(
                    chapterId = chapterId,
                    read = isRead,
                    lastPageRead = lastPageRead,
                )
            }
            // Check if the daily reading goal was just reached and notify if so.
            goalCompletionNotifier.checkAndNotify()
            Result.success()
        } catch (e: Exception) {
            // Retry on transient errors (e.g., DB locked).  WorkManager will back off
            // automatically; after the maximum retry count it will mark the work as failed.
            Result.retry()
        }
    }

    companion object {
        const val KEY_CHAPTER_ID = "chapter_id"
        const val KEY_READ_AT = "read_at"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_IS_INCOGNITO = "is_incognito"
        const val KEY_LAST_PAGE_READ = "last_page_read"
        const val KEY_IS_READ = "is_read"

        private const val INVALID_ID = -1L
        private const val INVALID_PAGE = -1

        /**
         * Builds a [WorkRequest] for persisting a reading session record.
         *
         * @param chapterId    The ID of the chapter that was read.
         * @param readAt       Epoch-millisecond timestamp for when the session started.
         * @param durationMs   Duration of the reading session in milliseconds.
         * @param isIncognito  When `true` the worker exits immediately without writing.
         * @param lastPageRead The last page the user was on (optional; omit to skip progress update).
         * @param isRead       Whether the chapter is considered fully read (used with [lastPageRead]).
         */
        fun buildRequest(
            chapterId: Long,
            readAt: Long,
            durationMs: Long,
            isIncognito: Boolean = false,
            lastPageRead: Int = INVALID_PAGE,
            isRead: Boolean = false,
        ): WorkRequest {
            val inputData: Data = workDataOf(
                KEY_CHAPTER_ID to chapterId,
                KEY_READ_AT to readAt,
                KEY_DURATION_MS to durationMs,
                KEY_IS_INCOGNITO to isIncognito,
                KEY_LAST_PAGE_READ to lastPageRead,
                KEY_IS_READ to isRead,
            )
            return OneTimeWorkRequestBuilder<RecordReadingHistoryWorker>()
                .setInputData(inputData)
                .build()
        }
    }
}
