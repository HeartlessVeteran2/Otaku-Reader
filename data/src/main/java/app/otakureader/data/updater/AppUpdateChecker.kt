package app.otakureader.data.updater

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.otakureader.core.preferences.GeneralPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing the latest app version information.
 */
@Serializable
data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val releaseDate: Long
)

/**
 * Worker that periodically checks for app updates from GitHub releases.
 * Uses [CoroutineWorker] so all preference reads and network calls run on
 * suspend-friendly coroutines instead of blocking the worker thread.
 */
@HiltWorker
class AppUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val generalPreferences: GeneralPreferences,
    private val appUpdateChecker: AppUpdateChecker
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isEnabled = generalPreferences.appUpdateCheckEnabled.first()
        if (!isEnabled) {
            return Result.success()
        }

        return try {
            val versionInfo = appUpdateChecker.checkForUpdate()
            generalPreferences.setLastAppUpdateCheck(System.currentTimeMillis())
            if (versionInfo != null) {
                generalPreferences.setLatestVersionInfo(Json.encodeToString(versionInfo))
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "app_update_check"

        /**
         * Schedule periodic app update checks.
         */
        fun schedule(context: Context, intervalHours: Int = 24) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(
                intervalHours.toLong(),
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }

        /**
         * Cancel periodic app update checks.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Run an immediate app update check.
         */
        fun checkNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AppUpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_once",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}

/**
 * Singleton class to manage app update checking against GitHub Releases.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalPreferences: GeneralPreferences,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val GITHUB_RELEASES_URL =
            "https://api.github.com/repos/HeartlessVeteran2/Otaku-Reader/releases/latest"
    }

    /**
     * Check if an update is available by querying the GitHub Releases API.
     * Returns [VersionInfo] if a newer version exists, null if up to date or on error.
     */
    suspend fun checkForUpdate(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = Json { ignoreUnknownKeys = true }
                .parseToJsonElement(body)
                .jsonObject

            val tagName = json["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
            val latestVersion = tagName.trimStart('v')
            val currentVersion = getCurrentVersionName()

            if (isNewerVersion(latestVersion, currentVersion)) {
                val publishedAt = json["published_at"]?.jsonPrimitive?.content
                VersionInfo(
                    versionCode = versionNameToCode(latestVersion),
                    versionName = latestVersion,
                    downloadUrl = json["html_url"]?.jsonPrimitive?.content ?: "",
                    releaseNotes = json["body"]?.jsonPrimitive?.content ?: "",
                    releaseDate = parseIso8601ToEpochMillis(publishedAt)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns true if [candidate] is strictly newer than [current].
     * Compares each dot-separated numeric component in order.
     */
    private fun isNewerVersion(candidate: String, current: String): Boolean {
        val candidateParts = candidate.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(candidateParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val c = candidateParts.getOrElse(i) { 0 }
            val cur = currentParts.getOrElse(i) { 0 }
            if (c > cur) return true
            if (c < cur) return false
        }
        return false
    }

    /** Converts a semver string like "1.2.3" to a comparable int (e.g. 10203). */
    private fun versionNameToCode(version: String): Int {
        val parts = version.split(".").mapNotNull { it.toIntOrNull() }
        return (parts.getOrElse(0) { 0 } * 10000) +
            (parts.getOrElse(1) { 0 } * 100) +
            parts.getOrElse(2) { 0 }
    }

    /** Parses an ISO-8601 date string to epoch millis; returns 0 on failure. */
    private fun parseIso8601ToEpochMillis(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0L
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    /**
     * Schedule periodic update checks.
     */
    fun scheduleChecks(intervalHours: Int = 24) {
        AppUpdateWorker.schedule(context, intervalHours)
    }

    /**
     * Cancel periodic update checks.
     */
    fun cancelChecks() {
        AppUpdateWorker.cancel(context)
    }
}
