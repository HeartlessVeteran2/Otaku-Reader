package app.otakureader.data.worker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages scheduling of periodic library update work.
 */
@Singleton
class LibraryUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Schedule (or reschedule) periodic library updates.
     */
    fun schedule(intervalHours: Int, wifiOnly: Boolean) {
        LibraryUpdateWorker.schedule(
            context = context,
            intervalHours = intervalHours,
            wifiOnly = wifiOnly
        )
    }

    /**
     * Cancel periodic library updates.
     */
    fun cancel() {
        LibraryUpdateWorker.cancelPeriodic(context)
    }
}
