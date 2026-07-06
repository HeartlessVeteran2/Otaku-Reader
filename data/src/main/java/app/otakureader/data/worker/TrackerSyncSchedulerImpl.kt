package app.otakureader.data.worker

import android.content.Context
import app.otakureader.domain.scheduler.TrackerSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerSyncSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TrackerSyncScheduler {

    override fun schedule(intervalHours: Int) {
        TrackerSyncWorker.schedule(context, intervalHours)
    }

    override fun cancel() {
        TrackerSyncWorker.cancel(context)
    }
}
