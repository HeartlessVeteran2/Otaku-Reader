package app.otakureader.data.history

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import app.otakureader.data.worker.RecordReadingHistoryWorker
import app.otakureader.domain.history.ReadingHistoryScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerHistoryScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ReadingHistoryScheduler {

    override fun scheduleExit(
        chapterId: Long,
        readAt: Long,
        durationMs: Long,
        isIncognito: Boolean,
        lastPageRead: Int,
        isRead: Boolean,
    ) {
        runCatching {
            val request = RecordReadingHistoryWorker.buildRequest(
                chapterId = chapterId,
                readAt = readAt,
                durationMs = durationMs,
                isIncognito = isIncognito,
                lastPageRead = lastPageRead,
                isRead = isRead,
            )
            WorkManager.getInstance(context).enqueue(request)
        }.onFailure { e ->
            Log.w(TAG, "Failed to enqueue RecordReadingHistoryWorker on reader exit", e)
        }
    }

    private companion object {
        const val TAG = "WorkManagerHistoryScheduler"
    }
}
