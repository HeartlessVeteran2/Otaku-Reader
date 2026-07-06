package app.otakureader.data.worker

import android.content.Context
import app.otakureader.domain.scheduler.ExtensionUpdateScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionUpdateSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExtensionUpdateScheduler {

    override fun schedule(intervalHours: Int, wifiOnly: Boolean) {
        ExtensionAutoUpdateWorker.schedule(context, intervalHours, wifiOnly)
    }

    override fun cancel() {
        ExtensionAutoUpdateWorker.cancel(context)
    }
}
