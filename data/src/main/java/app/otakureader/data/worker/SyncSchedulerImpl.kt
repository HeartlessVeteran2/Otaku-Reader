package app.otakureader.data.worker

import android.content.Context
import androidx.work.WorkManager
import app.otakureader.domain.scheduler.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-layer implementation of [SyncScheduler].
 *
 * Delegates to [SyncWorker]'s companion-object helpers, keeping WorkManager out
 * of the feature and domain layers.
 */
@Singleton
class SyncSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SyncScheduler {

    override fun schedulePeriodicSync() {
        SyncWorker.schedulePeriodicSync(WorkManager.getInstance(context))
    }

    override fun enqueueSingleSync() {
        SyncWorker.enqueueSingleSync(WorkManager.getInstance(context))
    }
}
