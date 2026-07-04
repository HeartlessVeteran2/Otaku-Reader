package app.otakureader.domain.scheduler

import kotlinx.coroutines.flow.Flow

interface LibraryUpdateScheduler {
    fun schedule(intervalHours: Int, wifiOnly: Boolean, requireCharging: Boolean = false)
    fun cancel()
    fun enqueueNow()

    /**
     * Emits true while a one-off library update is running, so the UI can show a progress
     * banner (matching Mihon/Komikku). Backed by the WorkManager unique-work state.
     */
    fun isUpdating(): Flow<Boolean>
}
