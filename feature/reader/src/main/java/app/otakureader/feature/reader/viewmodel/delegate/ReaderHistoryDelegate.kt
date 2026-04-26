package app.otakureader.feature.reader.viewmodel.delegate

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import app.otakureader.data.worker.RecordReadingHistoryWorker
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.data.repository.ReaderSettingsRepository
import app.otakureader.feature.reader.viewmodel.ReaderState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns reading-history persistence concerns for the reader:
 *  - recording the open of a chapter,
 *  - scheduling a [RecordReadingHistoryWorker] when the reader is closed,
 *  - the testable [cleanupOnExit] suspend implementation.
 *
 * Extracted from [app.otakureader.feature.reader.viewmodel.UltimateReaderViewModel]
 * so the ViewModel does not directly orchestrate WorkManager and DataStore reads.
 */
class ReaderHistoryDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterRepository: ChapterRepository,
    private val settingsRepository: ReaderSettingsRepository,
) {

    /**
     * Record that the user opened a chapter. Skips when the user is in
     * incognito mode (resolved directly from settings to avoid races with
     * concurrent settings loading).
     */
    fun recordOpen(
        scope: CoroutineScope,
        chapterId: Long,
        sessionReadAt: Long,
        fallbackIncognito: Boolean,
    ) {
        scope.launch {
            val isIncognito = runCatching {
                settingsRepository.incognitoMode.first()
            }.getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                fallbackIncognito
            }

            if (isIncognito) return@launch

            runCatching {
                chapterRepository.recordHistory(
                    chapterId = chapterId,
                    readAt = sessionReadAt,
                    readDurationMs = 0L,
                )
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    /**
     * Enqueue a [RecordReadingHistoryWorker] so history + progress are persisted
     * even if the OS kills the process before a raw coroutine could complete.
     *
     * Safe to call from `ViewModel.onCleared()` because WorkManager survives
     * the cancellation of [androidx.lifecycle.viewModelScope].
     */
    fun enqueueExit(
        chapterId: Long,
        sessionReadAt: Long,
        durationMs: Long,
        currentState: ReaderState,
    ) {
        runCatching {
            val request = RecordReadingHistoryWorker.buildRequest(
                chapterId = chapterId,
                readAt = sessionReadAt,
                durationMs = durationMs,
                isIncognito = currentState.incognitoMode,
                lastPageRead = currentState.currentPage,
                isRead = currentState.isLastPage,
            )
            WorkManager.getInstance(context).enqueue(request)
        }.onFailure { e ->
            Log.w(TAG, "Failed to enqueue RecordReadingHistoryWorker on reader exit", e)
        }
    }

    /**
     * Performs the final persistence work when the reader is closed. Kept as a
     * suspend function so it can be tested directly without going through the
     * `onCleared()` boundary.
     */
    suspend fun cleanupOnExit(
        chapterId: Long,
        sessionReadAt: Long,
        durationMs: Long,
        currentState: ReaderState,
    ) {
        val isIncognito = runCatching {
            settingsRepository.incognitoMode.first()
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            currentState.incognitoMode
        }

        if (isIncognito) return

        runCatching {
            chapterRepository.recordHistory(
                chapterId = chapterId,
                readAt = sessionReadAt,
                readDurationMs = durationMs,
            )
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
        runCatching {
            chapterRepository.updateChapterProgress(
                chapterId = chapterId,
                read = currentState.isLastPage,
                lastPageRead = currentState.currentPage,
            )
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    companion object {
        private const val TAG = "ReaderHistoryDelegate"
    }
}
