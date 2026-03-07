package app.otakureader.core.extension.domain.repository

import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.InstallStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing extensions.
 * Follows Clean Architecture - domain layer defines the contract.
 */
interface ExtensionRepository {
    
    /**
     * Get all installed extensions as a Flow for reactive updates.
     */
    fun getInstalledExtensions(): Flow<List<Extension>>
    
    /**
     * Get all available extensions from remote repository.
     */
    fun getAvailableExtensions(): Flow<List<Extension>>
    
    /**
     * Get extensions with available updates.
     */
    fun getExtensionsWithUpdates(): Flow<List<Extension>>
    
    /**
     * Get a single extension by package name.
     */
    suspend fun getExtension(pkgName: String): Extension?
    
    /**
     * Get extension by its unique ID.
     */
    suspend fun getExtensionById(id: Long): Extension?
    
    /**
     * Install an extension from a downloaded APK.
     * @param pkgName Package name of the extension
     * @param apkPath Path to the downloaded APK file
     * @return Result containing the installed Extension or an error
     */
    suspend fun installExtension(pkgName: String, apkPath: String): Result<Extension>
    
    /**
     * Uninstall an extension.
     * @param pkgName Package name to uninstall
     * @return Result indicating success or failure
     */
    suspend fun uninstallExtension(pkgName: String): Result<Unit>
    
    /**
     * Update an existing extension.
     * @param pkgName Package name to update
     * @param apkPath Path to the new APK file
     * @return Result containing the updated Extension or an error
     */
    suspend fun updateExtension(pkgName: String, apkPath: String): Result<Extension>
    
    /**
     * Check for available updates for all installed extensions.
     * @return Number of extensions with updates available
     */
    suspend fun checkForUpdates(): Int
    
    /**
     * Set the status of an extension (e.g., during installation).
     */
    suspend fun setExtensionStatus(pkgName: String, status: InstallStatus)
    
    /**
     * Refresh the list of available extensions from remote.
     */
    suspend fun refreshAvailableExtensions(): Result<Unit>
    
    /**
     * Search through installed and available extensions.
     */
    fun searchExtensions(query: String): Flow<List<Extension>>
}
