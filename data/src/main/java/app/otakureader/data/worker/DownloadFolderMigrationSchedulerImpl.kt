package app.otakureader.data.worker

import android.content.Context
import app.otakureader.domain.scheduler.DownloadFolderMigrationScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadFolderMigrationSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadFolderMigrationScheduler {
    override fun schedule() {
        DownloadFolderMigrationWorker.enqueue(context)
    }
}
