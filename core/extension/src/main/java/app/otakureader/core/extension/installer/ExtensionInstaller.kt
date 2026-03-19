package app.otakureader.core.extension.installer

import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSource
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.InstallStatus
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.loader.ExtensionLoadResult
import app.otakureader.core.extension.loader.ExtensionLoader
import app.otakureader.core.extension.receiver.ExtensionInstallReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Installation state for tracking progress.
 */
sealed class InstallationState {
    data object Idle : InstallationState()
    data class Downloading(val progress: Int) : InstallationState()
    data object Verifying : InstallationState()
    data object Installing : InstallationState()
    data class Success(val extension: Extension) : InstallationState()
    data class Error(val message: String, val throwable: Throwable? = null) : InstallationState()
}

/**
 * Handles APK installation, update, and removal for extensions.
 */
class ExtensionInstaller(
    private val context: Context,
    private val repository: ExtensionRepository,
    private val loader: ExtensionLoader,
    private val remoteDataSource: ExtensionRemoteDataSource
) {
    
    companion object {
        private const val EXTENSIONS_DIR = "extensions"
        private const val DOWNLOADS_DIR = "extension_downloads"
        private const val BUFFER_SIZE = 8192
    }
    
    private val _installationState = MutableStateFlow<InstallationState>(InstallationState.Idle)
    val installationState: Flow<InstallationState> = _installationState.asStateFlow()
    
    private val extensionsDir: File by lazy {
        File(context.filesDir, EXTENSIONS_DIR).apply { mkdirs() }
    }
    
    private val downloadsDir: File by lazy {
        File(context.cacheDir, DOWNLOADS_DIR).apply { mkdirs() }
    }

    /**
     * Download and install an extension from its APK URL.
     * @param extension The extension to install (must have apkUrl)
     * @return Result containing the installed Extension
     */
    suspend fun downloadAndInstall(extension: Extension): Result<Extension> = withContext(Dispatchers.IO) {
        try {
            val apkUrl = extension.apkUrl
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Extension has no APK URL")
                )

            _installationState.value = InstallationState.Downloading(0)

            // Generate a unique filename for the download
            val downloadFile = File(downloadsDir, "${UUID.randomUUID()}.apk")

            // Download the APK
            val downloadResult = remoteDataSource.downloadApk(apkUrl, downloadFile)
            if (downloadResult.isFailure) {
                _installationState.value = InstallationState.Error(
                    "Download failed: ${downloadResult.exceptionOrNull()?.message}",
                    downloadResult.exceptionOrNull()
                )
                return@withContext Result.failure(
                    downloadResult.exceptionOrNull() ?: Exception("Download failed")
                )
            }

            // Verify signature if available
            if (extension.signatureHash != null) {
                val isValid = verifySignature(downloadFile, extension.signatureHash)
                if (!isValid) {
                    downloadFile.delete()
                    _installationState.value = InstallationState.Error(
                        "Signature verification failed"
                    )
                    return@withContext Result.failure(
                        SecurityException("APK signature does not match expected hash")
                    )
                }
            }

            // Register the extension inside the app
            val result = install(downloadFile)

            result.onSuccess { installed ->
                installed.apkPath?.let { path ->
                    maybeLaunchSystemInstaller(File(path))
                }
            }

            result
        } catch (e: Exception) {
            _installationState.value = InstallationState.Error(
                "Installation failed: ${e.message}",
                e
            )
            Result.failure(e)
        }
    }

    /**
     * Install an extension from a downloaded APK file.
     * @param apkFile The downloaded APK file
     * @return Result containing the installed Extension
     */
    suspend fun install(apkFile: File): Result<Extension> = withContext(Dispatchers.IO) {
        try {
            _installationState.value = InstallationState.Verifying
            
            // Load and verify the extension
            val loadResult = loader.loadExtension(apkFile.absolutePath)
            
            when (loadResult) {
                is ExtensionLoadResult.Error -> {
                    _installationState.value = InstallationState.Error(
                        loadResult.message,
                        loadResult.throwable
                    )
                    Result.failure(
                        loadResult.throwable
                            ?: IllegalStateException(loadResult.message)
                    )
                }
                is ExtensionLoadResult.Success -> {
                    val extension = loadResult.extension

                    _installationState.value = InstallationState.Installing

                    // Move APK to permanent location
                    val destFile = File(extensionsDir, "${extension.pkgName}.apk")
                    apkFile.copyTo(destFile, overwrite = true)

                    // Update extension with final path
                    val finalExtension = extension.copy(apkPath = destFile.absolutePath)

                    // Save to repository
                    val result = repository.installExtension(
                        finalExtension.pkgName,
                        destFile.absolutePath
                    )

                    result.onSuccess { ext ->
                        _installationState.value = InstallationState.Success(ext)
                        // Notify the receiver so private extensions appear in the catalogue
                        ExtensionInstallReceiver.notifyAdded(context, finalExtension.pkgName)
                    }.onFailure { error ->
                        _installationState.value = InstallationState.Error(
                            "Failed to save extension: ${error.message}",
                            error
                        )
                    }

                    result
                }
                is ExtensionLoadResult.Untrusted -> {
                    // Note: ExtensionLoader never returns Untrusted; this branch is currently unreachable.
                    // Future: implement trust verification in ExtensionLoader.loadFromPackageInfo().
                    _installationState.value = InstallationState.Error(
                        "Extension is not trusted. Please verify its signature before installing.",
                        null
                    )
                    Result.failure(IllegalStateException("Untrusted extension: ${loadResult.extension.pkgName}"))
                }
            }
        } catch (e: Exception) {
            _installationState.value = InstallationState.Error("Installation failed", e)
            Result.failure(e)
        } finally {
            // Cleanup download file
            if (apkFile.exists() && apkFile.parentFile == downloadsDir) {
                apkFile.delete()
            }
        }
    }
    
    /**
     * Update an existing extension.
     * @param pkgName Package name of the extension to update
     * @param newApkFile The new APK file
     * @return Result containing the updated Extension
     */
    suspend fun update(pkgName: String, newApkFile: File): Result<Extension> = 
        withContext(Dispatchers.IO) {
            try {
                _installationState.value = InstallationState.Verifying
                
                // Verify the new APK
                val loadResult = loader.loadExtension(newApkFile.absolutePath)
                
                when (loadResult) {
                    is ExtensionLoadResult.Error -> {
                        _installationState.value = InstallationState.Error(
                            loadResult.message,
                            loadResult.throwable
                        )
                        Result.failure(
                            loadResult.throwable
                                ?: IllegalStateException(loadResult.message)
                        )
                    }
                    is ExtensionLoadResult.Success -> {
                        val extension = loadResult.extension

                        // Verify package name matches
                        if (extension.pkgName != pkgName) {
                            return@withContext Result.failure(
                                IllegalArgumentException(
                                    "Package name mismatch: expected $pkgName, got ${extension.pkgName}"
                                )
                            )
                        }

                        _installationState.value = InstallationState.Installing

                        // Remove old APK
                        val oldExtension = repository.getExtension(pkgName)
                        oldExtension?.apkPath?.let { oldPath ->
                            File(oldPath).delete()
                        }

                        // Move new APK to permanent location
                        val destFile = File(extensionsDir, "$pkgName.apk")
                        newApkFile.copyTo(destFile, overwrite = true)

                        // Update repository
                        val result = repository.updateExtension(pkgName, destFile.absolutePath)

                        result.onSuccess { ext ->
                            _installationState.value = InstallationState.Success(ext)
                            // Notify the receiver so private extensions appear in the catalogue
                            ExtensionInstallReceiver.notifyReplaced(context, pkgName)
                        }.onFailure { error ->
                            _installationState.value = InstallationState.Error(
                                "Failed to update extension: ${error.message}",
                                error
                            )
                        }

                        result
                    }
                    is ExtensionLoadResult.Untrusted -> {
                        // Note: ExtensionLoader never returns Untrusted; this branch is currently unreachable.
                        // Future: implement trust verification in ExtensionLoader.loadFromPackageInfo().
                        _installationState.value = InstallationState.Error(
                            "Extension is not trusted. Please verify its signature before updating.",
                            null
                        )
                        Result.failure(IllegalStateException("Untrusted extension: ${loadResult.extension.pkgName}"))
                    }
                }
            } catch (e: Exception) {
                _installationState.value = InstallationState.Error("Update failed", e)
                Result.failure(e)
            } finally {
                // Cleanup download file
                if (newApkFile.exists() && newApkFile.parentFile == downloadsDir) {
                    newApkFile.delete()
                }
            }
        }
    
    /**
     * Uninstall an extension.
     *
     * Distinguishes two cases:
     * - **System-installed (shared) extensions**: the package is registered with the
     *   Android PackageManager. Launching [Intent.ACTION_DELETE] triggers the system
     *   uninstaller dialog (requires [android.permission.REQUEST_DELETE_PACKAGES]).
     *   On user confirmation the system broadcasts [Intent.ACTION_PACKAGE_REMOVED],
     *   which [ExtensionInstallReceiver] receives to clean up the database entry.
     *   Any locally cached private APK copy is also removed immediately.
     * - **Private/sideloaded extensions**: stored only in the app's internal files dir
     *   and not registered with PackageManager. The local APK and database entry are
     *   deleted directly and a local removal broadcast is sent.
     *
     * @param pkgName Package name to uninstall
     * @return Result indicating success or failure
     */
    suspend fun uninstall(pkgName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            repository.setExtensionStatus(pkgName, InstallStatus.UNINSTALLING)

            if (isSystemInstalled(pkgName)) {
                // Trigger the system uninstaller dialog for shared/installed extensions.
                // The system will broadcast ACTION_PACKAGE_REMOVED on confirmation,
                // which ExtensionInstallReceiver handles to remove the DB entry.
                val deleteIntent = Intent(
                    Intent.ACTION_DELETE,
                    "package:$pkgName".toUri()
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(deleteIntent)

                // Remove any locally cached private APK copy for this package.
                File(extensionsDir, "$pkgName.apk").takeIf { it.exists() }?.delete()

                Result.success(Unit)
            } else {
                // Private/sideloaded extension: delete local APK and remove from DB.
                File(extensionsDir, "$pkgName.apk").takeIf { it.exists() }?.delete()

                // Remove from repository and notify the receiver.
                repository.uninstallExtension(pkgName).also {
                    ExtensionInstallReceiver.notifyRemoved(context, pkgName)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns true when [pkgName] is currently installed as a shared system package
     * discoverable via PackageManager. Private/sideloaded extensions stored only in
     * the app's internal files dir will return false.
     */
    private fun isSystemInstalled(pkgName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    pkgName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkgName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Verify APK signature against expected hash.
     * @param apkFile The APK file to verify
     * @param expectedHash Expected signature hash (optional, for trusted repos)
     * @return true if signature is valid or no hash provided
     */
    suspend fun verifySignature(apkFile: File, expectedHash: String?): Boolean = 
        withContext(Dispatchers.IO) {
            if (expectedHash == null) return@withContext true
            
            try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    context.packageManager.getPackageArchiveInfo(
                        apkFile.absolutePath,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageArchiveInfo(
                        apkFile.absolutePath,
                        PackageManager.GET_SIGNATURES
                    )
                }
                
                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo?.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo?.signatures
                }
                
                val actualHash = signatures?.firstOrNull()?.toByteArray()?.let {
                    computeHash(it)
                }
                
                actualHash == expectedHash
            } catch (e: Exception) {
                false
            }
        }
    
    /**
     * Get download directory for APKs.
     */
    fun getDownloadsDirectory(): File = downloadsDir
    
    /**
     * Get installation directory for extensions.
     */
    fun getExtensionsDirectory(): File = extensionsDir
    
    /**
     * Clear installation state.
     */
    fun resetState() {
        _installationState.value = InstallationState.Idle
    }

    /**
     * Optionally trigger the system package installer so extensions are registered
     * as shared packages. Falls back to internal loading if the permission is not
     * granted; opens the settings screen to request it.
     */
    private fun maybeLaunchSystemInstaller(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            // Ask the user to grant install permission for unknown apps
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
    
    private fun computeHash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
