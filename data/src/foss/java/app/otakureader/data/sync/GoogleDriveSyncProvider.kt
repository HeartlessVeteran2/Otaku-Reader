package app.otakureader.data.sync

import android.content.Context
import app.otakureader.core.preferences.SyncPreferences
import app.otakureader.domain.model.SyncSnapshot
import app.otakureader.domain.sync.SyncProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS stub — Google Drive sync is not available in FOSS builds because it requires
 * Google Play Services. All operations return a failure result so the sync manager
 * can skip this provider gracefully.
 */
@Singleton
class GoogleDriveSyncProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncPreferences: SyncPreferences,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : SyncProvider {

    override val id: String = "google_drive"
    override val name: String = "Google Drive"

    override suspend fun isAuthenticated(): Boolean = false

    override suspend fun authenticate(): Result<Unit> = unavailable()

    override suspend fun logout() = Unit

    override suspend fun uploadSnapshot(snapshot: SyncSnapshot): Result<Unit> = unavailable()

    override suspend fun downloadSnapshot(): Result<SyncSnapshot?> = unavailable()

    override suspend fun getLastSnapshotTime(): Long? = null

    override suspend fun deleteAllData(): Result<Unit> = unavailable()

    override suspend fun getAvailableSpace(): Long? = null

    private fun <T> unavailable(): Result<T> =
        Result.failure(UnsupportedOperationException("Google Drive sync is not available in FOSS builds"))
}
