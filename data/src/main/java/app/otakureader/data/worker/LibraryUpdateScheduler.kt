package app.otakureader.data.worker

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.otakureader.domain.scheduler.LibraryUpdateScheduler as LibraryUpdateSchedulerInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages scheduling of periodic library update work.
 */
@Singleton
class LibraryUpdateScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LibraryUpdateSchedulerInterface {

    override fun schedule(intervalHours: Int, wifiOnly: Boolean, requireCharging: Boolean) {
        LibraryUpdateWorker.schedule(
            context = context,
            intervalHours = intervalHours,
            wifiOnly = wifiOnly,
            requireCharging = requireCharging
        )
    }

    override fun cancel() {
        LibraryUpdateWorker.cancelPeriodic(context)
    }

    override fun enqueueNow() {
        LibraryUpdateWorker.enqueue(context)
    }

    /**
     * Emits `true` whenever a library update is running — whether it was triggered manually
     * (the one-off [LibraryUpdateWorker.WORK_NAME]) or by the periodic scheduler
     * ([LibraryUpdateWorker.PERIODIC_WORK_NAME]). We combine both unique-work flows so the
     * progress banner shows for either trigger, matching Mihon/Komikku.
     */
    override fun isUpdating(): Flow<Boolean> {
        val workManager = WorkManager.getInstance(context)
        return combine(
            workManager.getWorkInfosForUniqueWorkFlow(LibraryUpdateWorker.WORK_NAME),
            workManager.getWorkInfosForUniqueWorkFlow(LibraryUpdateWorker.PERIODIC_WORK_NAME),
        ) { oneOff, periodic ->
            oneOff.any { it.state == WorkInfo.State.RUNNING } ||
                periodic.any { it.state == WorkInfo.State.RUNNING }
        }
    }
}
