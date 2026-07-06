package app.otakureader.data.worker

import android.content.Context
import app.otakureader.domain.scheduler.CoverRefreshScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverRefreshSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : CoverRefreshScheduler {
    override fun schedule() {
        CoverRefreshWorker.enqueue(context)
    }
}
