package app.otakureader.domain.usecase.sync

import app.otakureader.domain.sync.SyncManager
import javax.inject.Inject

/**
 * Use case to get the timestamp of the last successful sync.
 *
 * @return Timestamp in milliseconds since epoch, or null if never synced
 */
class GetLastSyncTimeUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(): Long? {
        return syncManager.getLastSyncTime()
    }
}
