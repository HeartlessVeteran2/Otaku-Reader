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
import app.otakureader.core.preferences.DeleteAfterReadMode
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.resolveShouldDeleteAfterRead
import app.otakureader.domain.download.selectChapterToDeleteAfterRead
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.downloadFolderNameFor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

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
    private val downloadPreferences: DownloadPreferences,
    private val downloadRepository: DownloadRepository,
    private val mangaRepository: MangaRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val chapterId = inputData.getLong(KEY_CHAPTER_ID, INVALID_ID)
        val mangaId = inputData.getLong(KEY_MANGA_ID, INVALID_ID)
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
                if (isRead && mangaId != INVALID_ID) {
                    deleteDownloadIfEligible(mangaId = mangaId, chapterId = chapterId)
                }
            }
            // Check if the daily reading goal was just reached and notify if so.
            // Isolate notifier failures so they don't cause the worker to retry.
            try {
                goalCompletionNotifier.checkAndNotify()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("RecordReadingHistoryWorker", "Goal completion check failed", e)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Retry on transient errors (e.g., DB locked).  WorkManager will back off
            // automatically; after the maximum retry count it will mark the work as failed.
            Result.retry()
        }
    }

    /**
     * Deletes the chapter's downloaded pages when the global "delete after reading" preference
     * (or a per-manga override) says to. Mirrors [app.otakureader.feature.reader.viewmodel.delegate
     * .ReaderDeleteAfterReadDelegate], which handles the same decision for the live in-session
     * save path — this is the durable counterpart that still runs if the reader is closed before
     * that path's debounce timer fires, or the process dies before it completes.
     */
    private suspend fun deleteDownloadIfEligible(mangaId: Long, chapterId: Long) {
        val overrideMode = downloadPreferences.perMangaOverrides.first()[mangaId] ?: DeleteAfterReadMode.INHERIT
        val shouldDelete = resolveShouldDeleteAfterRead(
            overrideMode = overrideMode,
            globalEnabled = downloadPreferences.deleteAfterReading.first(),
        )
        if (!shouldDelete) return

        val manga = mangaRepository.getMangaById(mangaId) ?: return
        // slots > 0 keeps the last N read chapters downloaded and deletes the one N back instead.
        val slots = downloadPreferences.removeAfterReadSlots.first()
        val chapter = if (slots <= 0) {
            chapterRepository.getChapterById(chapterId)
        } else {
            selectChapterToDeleteAfterRead(
                chapters = chapterRepository.getChaptersByMangaIdSync(mangaId),
                justReadChapterId = chapterId,
                slots = slots,
            )
        } ?: return
        val downloadFolderName = downloadFolderNameFor(manga.sourceId)
        if (!downloadRepository.isChapterDownloaded(downloadFolderName, manga.title, chapter.name)) return

        downloadRepository.deleteChapterDownload(
            chapterId = chapter.id,
            sourceName = downloadFolderName,
            mangaTitle = manga.title,
            chapterTitle = chapter.name,
        )
    }

    companion object {
        const val KEY_CHAPTER_ID = "chapter_id"
        const val KEY_MANGA_ID = "manga_id"
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
         * @param mangaId      The ID of the chapter's parent manga, used to resolve delete-after-
         *                     reading eligibility.
         * @param readAt       Epoch-millisecond timestamp for when the session started.
         * @param durationMs   Duration of the reading session in milliseconds.
         * @param isIncognito  When `true` the worker exits immediately without writing.
         * @param lastPageRead The last page the user was on (optional; omit to skip progress update).
         * @param isRead       Whether the chapter is considered fully read (used with [lastPageRead]).
         */
        fun buildRequest(
            chapterId: Long,
            mangaId: Long,
            readAt: Long,
            durationMs: Long,
            isIncognito: Boolean = false,
            lastPageRead: Int = INVALID_PAGE,
            isRead: Boolean = false,
        ): WorkRequest {
            val inputData: Data = workDataOf(
                KEY_CHAPTER_ID to chapterId,
                KEY_MANGA_ID to mangaId,
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
