package app.otakureader.domain.usecase.sync

import app.otakureader.domain.sync.SyncManager
import javax.inject.Inject

/**
 * Use case to enable cloud sync with a specific provider.
 *
 * @param providerId Unique identifier for the sync provider (e.g., "google_drive", "dropbox")
 */
class EnableSyncUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(providerId: String): Result<Unit> {
        return syncManager.enableSync(providerId)
    }
}
