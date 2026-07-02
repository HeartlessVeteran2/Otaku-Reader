package app.otakureader.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.repository.DownloadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * One-time migration: download source folders used to be named after the numeric sourceId
 * (e.g. "1943584017") instead of the source's display name (e.g. "MangaDex"). Renames any
 * still-numeric folder to the resolved source name wherever the source can still be resolved.
 *
 * Gated on [GeneralPreferences.downloadFolderMigrationDone] rather than relying solely on
 * WorkManager's unique-work de-duplication: WorkManager can prune old completed-work records,
 * which would let this re-enqueue and re-run. That would be harmless — [DownloadRepository]'s
 * migrateSourceFolderNames is itself idempotent — but the DataStore flag makes the "never run
 * again" contract explicit and durable.
 */
@HiltWorker
class DownloadFolderMigrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloadRepository: DownloadRepository,
    private val generalPreferences: GeneralPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (generalPreferences.downloadFolderMigrationDone.first()) return Result.success()
        return try {
            downloadRepository.migrateSourceFolderNames()
            generalPreferences.setDownloadFolderMigrationDone(true)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(WORK_NAME, "Download folder migration failed", e)
            if (runAttemptCount >= MAX_RETRIES) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "download_folder_migration"
        private const val MAX_RETRIES = 3

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DownloadFolderMigrationWorker>().build()
            )
        }
    }
}
