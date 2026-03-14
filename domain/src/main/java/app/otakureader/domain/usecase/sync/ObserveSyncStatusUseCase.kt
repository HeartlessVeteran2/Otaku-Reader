package app.otakureader.domain.usecase.sync

import app.otakureader.domain.sync.SyncManager
import app.otakureader.domain.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to observe the current sync status.
 *
 * Returns a Flow that emits the current sync status (idle, syncing, success, error).
 */
class ObserveSyncStatusUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    operator fun invoke(): Flow<SyncStatus> {
        return syncManager.syncStatus
    }
}
