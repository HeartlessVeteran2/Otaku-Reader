package app.otakureader.domain.usecase.sync

import app.otakureader.domain.model.SyncResult
import app.otakureader.domain.sync.SyncManager
import javax.inject.Inject

/**
 * Use case to perform a manual sync operation.
 *
 * This will:
 * 1. Create a snapshot of local data
 * 2. Upload to cloud storage
 * 3. Download the latest remote snapshot
 * 4. Merge changes with conflict resolution
 * 5. Apply merged changes to local database
 */
class SyncNowUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(): Result<SyncResult> {
        return syncManager.sync()
    }
}
