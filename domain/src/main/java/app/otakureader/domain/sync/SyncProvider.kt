package app.otakureader.domain.sync

import app.otakureader.domain.model.SyncSnapshot

/**
 * Abstract interface for sync providers used for backing up and restoring data.
 *
 * Implementations can provide sync via self-hosted servers or other storage backends.
 * Each provider handles its own authentication and API specifics.
 */
interface SyncProvider {

    /**
     * Unique identifier for this provider (e.g., "self_hosted").
     */
    val id: String

    /**
     * Human-readable name shown in UI (e.g., "Self-Hosted Server").
     */
    val name: String

    /**
     * Whether the user is currently authenticated with this provider.
     */
    suspend fun isAuthenticated(): Boolean

    /**
     * Authenticate the user with this provider.
     *
     * For OAuth providers, this may open a browser flow.
     * For credential-based providers, this performs direct login.
     *
     * @return Result indicating success or failure with error details
     */
    suspend fun authenticate(): Result<Unit>

    /**
     * Clear stored credentials and log out.
     */
    suspend fun logout()

    /**
     * Upload a sync snapshot to cloud storage.
     *
     * @param snapshot The snapshot to upload
     * @return Result indicating success or failure
     */
    suspend fun uploadSnapshot(snapshot: SyncSnapshot): Result<Unit>

    /**
     * Download the latest sync snapshot from cloud storage.
     *
     * @return Result containing the snapshot or error details
     */
    suspend fun downloadSnapshot(): Result<SyncSnapshot?>

    /**
     * Get the timestamp of the last snapshot stored in the cloud.
     *
     * @return Timestamp in milliseconds since epoch, or null if no snapshot exists
     */
    suspend fun getLastSnapshotTime(): Long?

    /**
     * Delete all sync data from cloud storage.
     *
     * @return Result indicating success or failure
     */
    suspend fun deleteAllData(): Result<Unit>

    /**
     * Check available storage space (if applicable).
     *
     * @return Available bytes, or null if not supported/unlimited
     */
    suspend fun getAvailableSpace(): Long?
}
