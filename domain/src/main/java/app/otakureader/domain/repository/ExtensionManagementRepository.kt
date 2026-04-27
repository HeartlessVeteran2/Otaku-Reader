package app.otakureader.domain.repository

/**
 * Repository for extension lifecycle operations (install, reload, refresh).
 *
 * Kept separate from [SourceRepository] because these operations are only needed
 * by extension-management UI, not by source-browsing consumers.
 */
interface ExtensionManagementRepository {

    /** Load a Tachiyomi-compatible extension from a local APK file path. */
    suspend fun loadExtension(apkPath: String): Result<Unit>

    /** Download and load a Tachiyomi-compatible extension from a remote URL. */
    suspend fun loadExtensionFromUrl(url: String): Result<Unit>

    /** Reload all installed extensions and refresh the available-sources list. */
    suspend fun refreshSources()
}
