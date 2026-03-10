package app.otakureader.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.otakureader.core.preferences.BackupPreferences
import app.otakureader.data.backup.repository.BackupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Background worker that creates an automatic backup and saves it to local storage.
 *
 * On success it:
 *  - Prunes old backup files according to [BackupPreferences.autoBackupMaxCount].
 *  - Posts a success notification via [BackupNotifier].
 *
 * On failure it posts a failure notification so the user is aware.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val backupPreferences: BackupPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val notifier = BackupNotifier(applicationContext)
        return try {
            val file = backupRepository.createLocalBackup()

            val maxCount = backupPreferences.autoBackupMaxCount.first()
            backupRepository.pruneLocalBackups(maxCount)

            notifier.notifySuccess(file.name)
            Result.success()
        } catch (e: Exception) {
            notifier.notifyFailure(e.message)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "auto_backup"
    }
}
