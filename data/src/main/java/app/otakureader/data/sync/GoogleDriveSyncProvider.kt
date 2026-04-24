package app.otakureader.data.sync

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import app.otakureader.core.preferences.SyncPreferences
import app.otakureader.domain.model.SyncSnapshot
import app.otakureader.domain.sync.SyncProvider
import javax.inject.Inject
import javax.inject.Singleton

private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"
private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
private const val SNAPSHOT_FILE_NAME = "otakureader_sync.json"
private const val GOOGLE_ACCOUNT_TYPE = "com.google"

@Serializable
private data class DriveFileList(val files: List<DriveFile> = emptyList())

@Serializable
private data class DriveFile(val id: String = "", val modifiedTime: String = "")

/**
 * Sync provider backed by Google Drive app-data folder.
 *
 * Uses [GoogleAuthUtil] to obtain OAuth2 access tokens on the fly — no server-side
 * token exchange is needed. The user signs in once from the Settings screen, and their
 * account email is stored in [SyncPreferences]. Each sync operation calls
 * [GoogleAuthUtil.getToken] to get a fresh (auto-refreshed) token before making
 * Drive REST API calls.
 *
 * Snapshots are stored in the Drive app-data folder which is hidden from the user and
 * only accessible by this app.
 */
@Singleton
class GoogleDriveSyncProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncPreferences: SyncPreferences,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : SyncProvider {

    override val id: String = "google_drive"
    override val name: String = "Google Drive"

    override suspend fun isAuthenticated(): Boolean {
        val email = syncPreferences.getGoogleDriveEmail()
        if (email.isBlank()) return false
        return getAccessToken(email) != null
    }

    override suspend fun authenticate(): Result<Unit> {
        val email = syncPreferences.getGoogleDriveEmail()
        if (email.isBlank()) {
            return Result.failure(IllegalStateException("No Google account configured. Sign in from Settings."))
        }
        return if (getAccessToken(email) != null) Result.success(Unit)
        else Result.failure(IllegalStateException("Failed to obtain access token. Please sign in again."))
    }

    override suspend fun logout() {
        syncPreferences.clearGoogleDriveAccount()
    }

    override suspend fun uploadSnapshot(snapshot: SyncSnapshot): Result<Unit> {
        val token = requireToken() ?: return notAuthenticatedFailure()
        return withContext(Dispatchers.IO) {
            try {
                val snapshotJson = json.encodeToString(snapshot)
                val existingId = findSnapshotFileId(token)

                val result = if (existingId != null) {
                    updateDriveFile(token, existingId, snapshotJson)
                } else {
                    createDriveFile(token, snapshotJson)
                }

                if (result) {
                    syncPreferences.setLastSyncTimestamp(snapshot.createdAt)
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Drive upload failed"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun downloadSnapshot(): Result<SyncSnapshot?> {
        val token = requireToken() ?: return notAuthenticatedFailure()
        return withContext(Dispatchers.IO) {
            try {
                val fileId = findSnapshotFileId(token) ?: return@withContext Result.success(null)
                val snapshotJson = downloadDriveFileContent(token, fileId)
                    ?: return@withContext Result.success(null)
                val snapshot = json.decodeFromString<SyncSnapshot>(snapshotJson)
                Result.success(snapshot)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getLastSnapshotTime(): Long? {
        val token = requireToken() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$DRIVE_FILES_URL?spaces=appDataFolder&fields=files(id,modifiedTime)&q=name='$SNAPSHOT_FILE_NAME'")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val fileList = json.decodeFromString<DriveFileList>(body)
                    val modifiedTime = fileList.files.firstOrNull()?.modifiedTime
                        ?: return@withContext null
                    java.time.Instant.parse(modifiedTime).toEpochMilli()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun deleteAllData(): Result<Unit> {
        val token = requireToken() ?: return notAuthenticatedFailure()
        return withContext(Dispatchers.IO) {
            try {
                val fileId = findSnapshotFileId(token) ?: return@withContext Result.success(Unit)
                val request = Request.Builder()
                    .url("$DRIVE_FILES_URL/$fileId")
                    .header("Authorization", "Bearer $token")
                    .delete()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(IllegalStateException("Delete failed: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getAvailableSpace(): Long? = null // App data folder has no meaningful quota

    // ── Internal helpers ──────────────────────────────────────────────────

    private suspend fun getAccessToken(email: String): String? = withContext(Dispatchers.IO) {
        try {
            val account = Account(email, GOOGLE_ACCOUNT_TYPE)
            GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun requireToken(): String? {
        val email = syncPreferences.getGoogleDriveEmail()
        if (email.isBlank()) return null
        return getAccessToken(email)
    }

    private fun findSnapshotFileId(token: String): String? {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL?spaces=appDataFolder&fields=files(id)&q=name='$SNAPSHOT_FILE_NAME'")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            json.decodeFromString<DriveFileList>(body).files.firstOrNull()?.id?.takeIf { it.isNotBlank() }
        }
    }

    private fun createDriveFile(token: String, snapshotJson: String): Boolean {
        val metadata = """{"name":"$SNAPSHOT_FILE_NAME","parents":["appDataFolder"]}"""
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata", null,
                metadata.toRequestBody("application/json".toMediaType())
            )
            .addFormDataPart(
                "file", SNAPSHOT_FILE_NAME,
                snapshotJson.toRequestBody("application/json".toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart&spaces=appDataFolder")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return okHttpClient.newCall(request).execute().use { it.isSuccessful }
    }

    private fun updateDriveFile(token: String, fileId: String, snapshotJson: String): Boolean {
        val requestBody = snapshotJson.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL/$fileId?uploadType=media")
            .header("Authorization", "Bearer $token")
            .patch(requestBody)
            .build()
        return okHttpClient.newCall(request).execute().use { it.isSuccessful }
    }

    private fun downloadDriveFileContent(token: String, fileId: String): String? {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun <T> notAuthenticatedFailure(): Result<T> =
        Result.failure(IllegalStateException("Not authenticated with Google Drive"))
}
