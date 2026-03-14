package app.otakureader.data.sync

import app.otakureader.domain.model.SyncSnapshot
import app.otakureader.domain.sync.SyncProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dropbox implementation of SyncProvider (stub for future implementation).
 *
 * This is a placeholder implementation. A production implementation should:
 * - Use Dropbox SDK for Android
 * - Implement OAuth 2.0 authentication
 * - Store sync file in app-specific folder
 * - Handle token refresh
 * - Implement retry logic with exponential backoff
 * - Add proper error handling
 *
 * For implementation guide, see:
 * https://www.dropbox.com/developers/documentation/java
 */
@Singleton
class DropboxSyncProvider @Inject constructor() : SyncProvider {

    override val id: String = PROVIDER_ID
    override val name: String = "Dropbox"

    override val isAuthenticated: Boolean
        get() = false // Stub: Not implemented

    override suspend fun authenticate(): Result<Unit> {
        return Result.failure(
            NotImplementedError(
                "Dropbox sync requires Dropbox SDK integration. " +
                "Add implementation using Dropbox Core API with OAuth 2.0."
            )
        )
    }

    override suspend fun logout() {
        // Stub: Not implemented
    }

    override suspend fun uploadSnapshot(snapshot: SyncSnapshot): Result<Unit> {
        return Result.failure(NotImplementedError("Dropbox sync not implemented"))
    }

    override suspend fun downloadSnapshot(): Result<SyncSnapshot?> {
        return Result.failure(NotImplementedError("Dropbox sync not implemented"))
    }

    override suspend fun getLastSnapshotTime(): Long? {
        return null // Stub: Not implemented
    }

    override suspend fun deleteAllData(): Result<Unit> {
        return Result.failure(NotImplementedError("Dropbox sync not implemented"))
    }

    override suspend fun getAvailableSpace(): Long? {
        return null // Stub: Not implemented
    }

    companion object {
        const val PROVIDER_ID = "dropbox"
    }
}
