package app.otakureader.feature.reader.viewmodel.delegate

import android.content.Context
import android.util.Log
import app.otakureader.domain.history.ReadingHistoryScheduler
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.ReaderSettingsRepository
import app.otakureader.feature.reader.ReaderState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns reading-history persistence concerns for the reader:
 *  - recording the open of a chapter,
 *  - scheduling a history worker when the reader is closed,
 *  - the testable [cleanupOnExit] suspend implementation.
 *
 * Extracted from [app.otakureader.feature.reader.ReaderViewModel]
 * so the ViewModel does not directly orchestrate WorkManager and DataStore reads.
 */
class ReaderHistoryDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chapterRepository: ChapterRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val historyScheduler: ReadingHistoryScheduler,
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
     * Schedule durable history persistence via [ReadingHistoryScheduler].
     *
     * Safe to call from `ViewModel.onCleared()` because the scheduler implementation
     * (WorkManager) survives the cancellation of [androidx.lifecycle.viewModelScope].
     */
    fun enqueueExit(
        chapterId: Long,
        sessionReadAt: Long,
        durationMs: Long,
        currentState: ReaderState,
    ) {
        historyScheduler.scheduleExit(
            chapterId = chapterId,
            readAt = sessionReadAt,
            durationMs = durationMs,
            isIncognito = currentState.incognitoMode,
            lastPageRead = currentState.currentPage,
            isRead = currentState.isLastPage,
        )
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
        @Suppress("unused")
        private const val TAG = "ReaderHistoryDelegate"
    }
}
