package app.otakureader.domain.usecase.sync

import app.otakureader.domain.sync.SyncManager
import javax.inject.Inject

/**
 * Use case to disable cloud sync.
 *
 * @param clearMetadata If true, clears local sync metadata (last sync time, provider, etc.)
 */
class DisableSyncUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(clearMetadata: Boolean = false) {
        syncManager.disableSync(clearMetadata)
    }
}
