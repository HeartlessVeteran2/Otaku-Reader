package app.otakureader.core.extension.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSource
import app.otakureader.core.extension.domain.backend.JsExtensionBackend
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
import kotlinx.coroutines.CancellationException

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
    private val remoteDataSource: ExtensionRemoteDataSource,
    /**
     * The JavaScript backend, when one is wired in.
     *
     * Dispatch happens here rather than in the ViewModel so `feature/browse` stays unaware that
     * there are two backends at all — it calls one install method with an [Extension] and gets
     * the right behaviour. Null keeps the APK-only path, which is what the existing tests cover.
     */
    private val jsBackend: JsExtensionBackend? = null,
) {
    
    companion object {
        private const val EXTENSIONS_DIR = "exts"
        private const val DOWNLOADS_DIR = "extension_downloads"
    }
    
    private val _installationState = MutableStateFlow<InstallationState>(InstallationState.Idle)
    val installationState: Flow<InstallationState> = _installationState.asStateFlow()
    
    private val extensionsDir: File by lazy {
        File(context.filesDir, EXTENSIONS_DIR).apply { mkdirs() }
    }
    
    private val downloadsDir: File by lazy {
        File(context.filesDir, DOWNLOADS_DIR).apply { mkdirs() }
    }

    /**
     * Download and install an extension from its APK URL.
     *
     * **Security contract**: Only HTTPS URLs are accepted. The caller is responsible for
     * displaying a user-facing confirmation dialog before invoking this method when the
     * extension originates from an untrusted or user-supplied URL (i.e.
     * [extension.signatureHash] is null). This method enforces HTTPS at the transport
     * layer but cannot verify the trustworthiness of the remote host.
     *
     * @param extension The extension to install (must have apkUrl)
     * @return Result containing the installed Extension
     */
    suspend fun downloadAndInstall(extension: Extension): Result<Extension> = withContext(Dispatchers.IO) {
        try {
            if (extension.isJavaScript) {
                return@withContext installJavaScript(extension)
            }

            val apkUrl = extension.apkUrl
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Extension has no APK URL")
                )

            // C-3: Reject non-HTTPS URLs to prevent man-in-the-middle attacks.
            if (!apkUrl.startsWith("https://")) {
                return@withContext Result.failure(
                    SecurityException(
                        "Extension APK URL must use HTTPS. Insecure URL rejected: $apkUrl"
                    )
                )
            }

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

            // Register the extension inside the app, passing the repo-verified hash so the
            // universal trust gate inside ExtensionLoader can be satisfied without a separate
            // user confirmation step. The repository-backed extension rides along so a
            // fallback-created DB row keeps provenance (#1019) plus apkUrl/iconUrl metadata.
            // Private-installer model (matches Komikku's default): install() copies the APK into
            // the app's private extensions dir, loads/validates it, trusts repo-sourced signatures,
            // and persists the DB row. That is fully self-sufficient — the source becomes loadable
            // immediately and the caller's refreshSources() picks it up. We deliberately do NOT
            // also launch the system package installer here: doing so popped a second, confusing
            // "install this app?" dialog (and an unknown-sources settings redirect) on top of an
            // already-successful silent install.
            install(
                downloadFile,
                trustedHash = extension.signatureHash,
                repoMetadata = extension,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _installationState.value = InstallationState.Error(
                "Installation failed: ${e.message}",
                e
            )
            Result.failure(e)
        }
    }

    /**
     * Drop the extension's row and announce the removal — but only announce it if the row went.
     *
     * Both uninstall paths reach here having already destroyed the artifact: the APK path has
     * deleted its private copy, the JavaScript path has deregistered the script and erased the
     * source's stored preferences. So by this point the extension really is gone, and the row is
     * the last thing describing something that no longer exists.
     *
     * Two things were wrong when each path did this inline, and they were wrong identically —
     * which is why this is now one function instead of two copies:
     *
     * - `.also { notifyRemoved(...) }` runs on the `Result` regardless of whether it succeeded,
     *   so a failed delete still broadcast a removal. Listeners then dropped their state for an
     *   extension the database still lists. `onSuccess` fires only on success.
     * - A failed delete left the row stuck at `UNINSTALLING` forever: a source frozen
     *   mid-removal that the user cannot act on and that no longer works.
     *
     * `ERROR` is the honest terminal state for that failure. Not `INSTALLED` — the extension is
     * genuinely gone, and a row claiming otherwise would show a source that cannot function.
     * `ERROR` also self-corrects: `refreshAvailableExtensions` deliberately does not treat ERROR
     * rows as installed, so the next refresh re-offers the extension as available to install.
     */
    private suspend fun finalizeRemoval(pkgName: String): Result<Unit> =
        repository.uninstallExtension(pkgName)
            .onSuccess { ExtensionInstallReceiver.notifyRemoved(context, pkgName) }
            .onFailure { repository.setExtensionStatus(pkgName, InstallStatus.ERROR) }

    /**
     * Remove a JavaScript source's script, registration and stored data, then its row.
     *
     * The backend runs first and the row is dropped only if it succeeded. The reverse order
     * would let a failed erase leave the credentials on disk with the source already gone from
     * the list the user would retry from — a state no retry can reach. `JsSourceProvider`
     * applies the same ordering internally for the same reason.
     */
    private suspend fun uninstallJavaScript(pkgName: String): Result<Unit> {
        val backend = jsBackend ?: return Result.failure(
            IllegalStateException("No JavaScript backend is available to uninstall $pkgName")
        )

        return backend.uninstall(pkgName)
            .fold(
                onSuccess = { finalizeRemoval(pkgName) },
                onFailure = { error ->
                    // Put the row back to INSTALLED. Leaving it UNINSTALLING would show a
                    // source stuck mid-removal that the user cannot act on, while the source
                    // itself is still perfectly functional.
                    repository.setExtensionStatus(pkgName, InstallStatus.INSTALLED)
                    Result.failure(error)
                },
            )
    }

    /**
     * Install a JavaScript source: fetch the script, register it, then record the row.
     *
     * The order is the point. The database row is written *last*, only once the script is on
     * disk and registered with the engine, so a failed download or a rejected non-HTTPS URL
     * leaves nothing behind that claims to be installed. Writing the row first would produce a
     * source that appears in the library and fails on every call — which is precisely the class
     * of silent, undiagnosable failure this rebuild exists to eliminate.
     *
     * There is no APK, no `PackageManager`, no signature verification and no system install
     * prompt on this path. That is the whole reason the backend exists, and it is also why the
     * HTTPS check inside the remote data source is not optional: with no signature to verify,
     * transport is the only control on what gets executed.
     */
    private suspend fun installJavaScript(extension: Extension): Result<Extension> {
        val backend = jsBackend ?: return Result.failure(
            IllegalStateException("No JavaScript backend is available to install ${extension.pkgName}")
        )

        _installationState.value = InstallationState.Downloading(0)

        val installed = backend.install(extension)
        if (installed.isFailure) {
            val error = installed.exceptionOrNull() ?: Exception("JavaScript install failed")
            _installationState.value = InstallationState.Error(
                "Installation failed: ${error.message}",
                error
            )
            return Result.failure(error)
        }

        _installationState.value = InstallationState.Installing

        // apkPath is empty rather than a path: there is no APK file to point at.
        return repository.installExtension(extension, apkPath = "")
            .onSuccess { _installationState.value = InstallationState.Success(it) }
            .onFailure { error -> rollBackOrphanedScript(extension, backend, error) }
    }

    /**
     * Clean up after a JavaScript install whose database write failed.
     *
     * The hazard is an orphan: the script is on disk and registered with the engine, and since
     * uninstall finds its target *through the database*, a missing row means nothing can ever
     * reach it again — a source that still executes and cannot be removed.
     *
     * **The rollback is conditional, and that condition is the whole correctness of this.** An
     * unconditional `backend.uninstall` fixes the fresh-install orphan and breaks something
     * worse in the update case: `backend.install` has already replaced the script of a source
     * the user had working, so uninstalling would delete it outright — along with the stored
     * preferences that routinely hold their login for that site. The cure would be worse than
     * the failure it followed.
     *
     * The rule both branches follow is simply **make the row describe reality**:
     *
     * - **No row existed.** Nothing installed this before, and the failed write left nothing
     *   behind (`updateStatus` on an absent row is a no-op). The script is unreachable, so it
     *   goes.
     * - **A row existed.** Something *is* installed — the new script is on disk and registered,
     *   and it is a valid script. So the row is restored to `INSTALLED`, which is the honest
     *   status, rather than left at the `ERROR` the repository set on its way out.
     *
     * Restoring the status is not cosmetic. `ERROR` is deliberately transient: the next
     * `refreshAvailableExtensions` does not count `ERROR` rows as installed and replaces them
     * with fresh `AVAILABLE` ones. The script would then be live in Browse and registered with
     * the engine while the extension screen listed it as not installed — no uninstall button
     * anywhere, which is the orphan this method exists to prevent, just reached a slower way.
     *
     * The same principle produced the opposite answer in [finalizeRemoval], which sets `ERROR`
     * after a failed *delete*: there the extension really is gone, so letting the row lapse to
     * available is right. Here it really is present.
     *
     * Residual, stated rather than papered over: a restored row still names the *previous*
     * version while the new script is on disk. The source works, and the next update check
     * reconciles the version. Snapshotting every script before every update to restore the old
     * one would be a lot of machinery for a stale version number.
     */
    private suspend fun rollBackOrphanedScript(
        extension: Extension,
        backend: JsExtensionBackend,
        error: Throwable,
    ) {
        // Three outcomes, not two. `runCatching { ... }.getOrNull()` collapsed "the row is
        // absent" and "the lookup itself failed" into the same null — and the destructive branch
        // hangs off that value. This code runs *because* a database write just failed, so a
        // follow-up read failing is a likely path rather than a theoretical one, and treating it
        // as proof of absence would delete a working source and the user's saved login for it.
        //
        // Destroying data requires positive evidence that it is safe. Absence of evidence is not
        // that, so a failed lookup takes the non-destructive branch.
        val lookup = try {
            Result.success(repository.getExtension(extension.pkgName))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            runCatching { error.addSuppressed(e) }
            Result.failure(e)
        }

        val existingRow = lookup.getOrNull()

        if (lookup.isSuccess && existingRow == null) {
            // The cleanup's own failure must not replace the error that caused all this — that
            // would send the user looking in the wrong place. It is attached instead, so an
            // orphan that could not be cleaned up is still diagnosable from the reported error.
            backend.uninstall(extension.pkgName).onFailure { cleanupError ->
                runCatching { error.addSuppressed(cleanupError) }
            }
        } else {
            runCatching {
                repository.setExtensionStatus(extension.pkgName, InstallStatus.INSTALLED)
            }
        }

        _installationState.value = InstallationState.Error(
            "Installation failed: ${error.message}",
            error
        )
    }

    /**
     * Atomically replace [targetFile] with [tempFile].
     *
     * Tries API 26+ [java.nio.file.Files.move] with [ATOMIC_MOVE] + [REPLACE_EXISTING]
     * first. On older Android or when the atomic move fails, falls back to a
     * backup-then-rename strategy that preserves the original file if anything
     * goes wrong. Last resort is copy-overwrite.
     *
     * @return true on success. On failure [targetFile] is left untouched if possible.
     */
    private fun replaceAtomically(tempFile: File, targetFile: File): Boolean {
        if (!tempFile.exists()) return false

        // API 26+: true atomic move with replace
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                return true
            } catch (_: Exception) {
                // Fall through to fallback
            }
        }

        // Pre-API 26: try renameTo first (fast, atomic on same filesystem).
        // renameTo does not overwrite an existing file on Android.
        if (!targetFile.exists()) {
            if (tempFile.renameTo(targetFile)) {
                return true
            }
        } else {
            // Backup the existing file, then rename temp into place.
            val backupFile = File(targetFile.parentFile, "${targetFile.name}.bak")
            if (targetFile.renameTo(backupFile)) {
                if (tempFile.renameTo(targetFile)) {
                    backupFile.delete() // Commit: discard backup
                    return true
                }
                // Rollback: restore original file
                backupFile.renameTo(targetFile)
                return false
            }
        }

        // Last resort: copy temp over target, then delete temp.
        // Not atomic, but keeps target valid if the copy throws.
        return try {
            tempFile.copyTo(targetFile, overwrite = true)
            targetFile.setReadOnly()
            tempFile.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Install an extension from a downloaded APK file.
     *
     * **Transactional contract**: writes to a temp file (`$pkgName.ext.tmp`), loads and
     * validates the extension from that temp file, and only after every check passes
     * atomically renames the temp file to the permanent location (`$pkgName.ext`).
     * If any step fails the original extension (if any) is left untouched and the temp
     * file is deleted.
     *
     * @param apkFile The downloaded APK file.
     * @param trustedHash When non-null this hash was already verified by the caller against
     *   a repository-sourced expected hash. The extension's actual loaded hash is compared
     *   against this value; the trust store is only updated **after** the extension has been
     *   successfully loaded and moved to its permanent location.
     * @param repoMetadata The repository-index-backed extension this APK was downloaded
     *   for, when known. The APK loader only knows manifest-derived fields, so repoUrl
     *   (install provenance, #1019), apkUrl, and iconUrl are merged from this object —
     *   without it, a fallback-created DB row would have null sourceRepoUrl and the
     *   cross-repo replacement guard would be inactive until the next update check.
     */
    suspend fun install(
        apkFile: File,
        trustedHash: String? = null,
        repoMetadata: Extension? = null,
    ): Result<Extension> =
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                _installationState.value = InstallationState.Verifying

                val packageInfo = parseApkInfo(apkFile)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Failed to parse APK: ${apkFile.absolutePath}")
                    )

                val pkgName = packageInfo.packageName
                val destFile = File(extensionsDir, "$pkgName.ext")
                tempFile = File(extensionsDir, "$pkgName.ext.tmp")

                apkFile.copyTo(tempFile, overwrite = true)
                tempFile.setReadOnly()

                val loadResult = loader.loadExtension(tempFile.absolutePath)
                val extension = resolveLoadResult(loadResult, trustedHash)
                    .getOrElse { return@withContext Result.failure(it) }

                if (!replaceAtomically(tempFile, destFile)) {
                    return@withContext Result.failure(
                        IllegalStateException("Failed to move extension to permanent location: ${destFile.absolutePath}")
                    )
                }
                tempFile = null

                val hashToTrust = resolveAutoTrustHash(loadResult, trustedHash, repoMetadata)

                _installationState.value = InstallationState.Installing
                // Merge repo-index metadata the APK loader can't know about.
                // Also restore signatureHash so Extension.isTrusted = true when auto-trusted.
                val finalExtension = extension.copy(
                    apkPath = destFile.absolutePath,
                    signatureHash = hashToTrust ?: extension.signatureHash,
                    repoUrl = extension.repoUrl ?: repoMetadata?.repoUrl,
                    apkUrl = extension.apkUrl ?: repoMetadata?.apkUrl,
                    iconUrl = extension.iconUrl ?: repoMetadata?.iconUrl,
                )
                // Pass the fully-loaded extension so the install succeeds even when the
                // database has no row yet (e.g. repo was just added and the available-list
                // refresh hasn't synced).
                val result = repository.installExtension(finalExtension, destFile.absolutePath)
                result.onSuccess { ext ->
                    // Persist trust only after the DB write succeeds — trusting before means a
                    // failed install leaves the signature permanently trusted in the store.
                    if (hashToTrust != null) loader.trustExtension(hashToTrust)
                    _installationState.value = InstallationState.Success(ext)
                    ExtensionInstallReceiver.notifyAdded(context, finalExtension.pkgName)
                }.onFailure { error ->
                    _installationState.value = InstallationState.Error("Failed to save extension: ${error.message}", error)
                }
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _installationState.value = InstallationState.Error("Installation failed", e)
                Result.failure(e)
            } finally {
                tempFile?.delete()
                if (apkFile.exists() && apkFile.parentFile == downloadsDir) apkFile.delete()
            }
        }

    /**
     * Determines which signature hash (if any) to auto-trust after installation.
     *
     * Auto-trusts when:
     * (a) the repo provided a hash that matches the installed APK, or
     * (b) the install came from a configured repository (repoMetadata != null) with no
     *     repo-provided hash (e.g. Keiyoushi/Komikku minified index.min.json). Users
     *     explicitly add repos they trust; requiring a per-extension second trust screen
     *     is unnecessary friction. Sideloaded APKs (repoMetadata == null) still require
     *     manual trust via ExtensionDetailScreen.
     */
    private fun resolveAutoTrustHash(
        loadResult: ExtensionLoadResult,
        trustedHash: String?,
        repoMetadata: Extension?,
    ): String? {
        val actualHash: String? = when (loadResult) {
            is ExtensionLoadResult.Success -> loadResult.extension.signatureHash
            is ExtensionLoadResult.Untrusted -> loadResult.extension.signatureHash
            else -> null
        }
        return when {
            trustedHash != null && trustedHash == actualHash -> trustedHash
            trustedHash == null && repoMetadata != null && actualHash != null -> actualHash
            else -> null
        }
    }

    private fun resolveLoadResult(
        loadResult: ExtensionLoadResult,
        trustedHash: String?
    ): Result<Extension> = when (loadResult) {
        is ExtensionLoadResult.Success -> {
            val ext = loadResult.extension
            if (trustedHash != null && ext.signatureHash != trustedHash) {
                _installationState.value = InstallationState.Error("Extension trust hash mismatch", null)
                Result.failure(SecurityException("Trust hash mismatch for ${ext.pkgName}"))
            } else {
                Result.success(ext)
            }
        }
        is ExtensionLoadResult.Untrusted -> {
            val ext = loadResult.extension
            // Reject only on a real hash mismatch — someone could be swapping the APK.
            // When no trusted hash is available (e.g. Keiyoushi/Mihon minified index.min.json
            // doesn't include a signing-cert hash), install the APK anyway so the user can
            // review it and tap "Trust" on the detail screen.
            if (trustedHash != null && ext.signatureHash != trustedHash) {
                _installationState.value = InstallationState.Error("Extension trust hash mismatch", null)
                Result.failure(SecurityException("Trust hash mismatch for ${ext.pkgName}"))
            } else {
                // The extension is NOT trusted yet. Persist it with a null signatureHash so
                // Extension.isTrusted (== signatureHash != null) stays false and the UI shows
                // "Unverified" + Trust button. trustExtension() recomputes the hash from the
                // installed APK when the user taps Trust. Without this, the APK-derived hash
                // would be stored and the UI would falsely show "Verified" while the loader
                // still rejects the source as untrusted (state mismatch → "No sources").
                Result.success(ext.copy(signatureHash = null))
            }
        }
        is ExtensionLoadResult.Error -> {
            _installationState.value = InstallationState.Error(loadResult.message, loadResult.throwable)
            Result.failure(loadResult.throwable ?: IllegalStateException(loadResult.message))
        }
    }
    
    /**
     * Update an existing extension.
     *
     * **Transactional contract**: writes the new APK to a temp file (`$pkgName.ext.tmp`),
     * parses it, loads the extension from the temp file, and verifies signer continuity
     * against the currently installed version. Only after every validation step passes
     * is the temp file atomically promoted to the permanent location (`$pkgName.ext`).
     * If any step fails the original extension remains untouched and the temp file is
     * cleaned up.
     *
     * @param pkgName Package name of the extension to update
     * @param newApkFile The new APK file
     * @return Result containing the updated Extension
     */
    @Suppress("LongMethod")
    suspend fun update(pkgName: String, newApkFile: File): Result<Extension> =
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                _installationState.value = InstallationState.Verifying

                val newPackageInfo = parseApkInfo(newApkFile)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Failed to parse update APK: ${newApkFile.absolutePath}")
                    )

                if (newPackageInfo.packageName != pkgName) {
                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Package name mismatch: expected $pkgName, got ${newPackageInfo.packageName}"
                        )
                    )
                }

                val destFile = File(extensionsDir, "$pkgName.ext")
                tempFile = File(extensionsDir, "$pkgName.ext.tmp")

                // 1. Write to temp file — preserve the working artifact until validation passes.
                newApkFile.copyTo(tempFile, overwrite = true)
                tempFile.setReadOnly()

                // 2. Load and validate from the temp file.
                val loadResult = loader.loadExtension(tempFile.absolutePath)

                val extension = when (loadResult) {
                    is ExtensionLoadResult.Success -> loadResult.extension
                    is ExtensionLoadResult.Untrusted -> {
                        _installationState.value = InstallationState.Error(
                            "Extension is not trusted. Please verify its signature before updating.",
                            null
                        )
                        return@withContext Result.failure(
                            IllegalStateException("Untrusted extension: ${loadResult.extension.pkgName}")
                        )
                    }
                    is ExtensionLoadResult.Error -> {
                        _installationState.value = InstallationState.Error(
                            loadResult.message, loadResult.throwable
                        )
                        return@withContext Result.failure(
                            loadResult.throwable ?: IllegalStateException(loadResult.message)
                        )
                    }
                }
                // 3. Signer continuity: reject if signing certificate changed — a compromised
                // repository could swap a trusted extension for a differently-signed one.
                val oldExtension = repository.getExtension(pkgName)
                val newHash = extension.signatureHash
                if (oldExtension?.signatureHash != null && newHash != oldExtension.signatureHash) {
                    return@withContext Result.failure(
                        SecurityException(
                            "Extension update rejected: signing certificate changed for $pkgName"
                        )
                    )
                }

                // 4. Atomic rename temp → permanent.
                if (!replaceAtomically(tempFile, destFile)) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Failed to move extension to permanent location: ${destFile.absolutePath}"
                        )
                    )
                }
                tempFile = null // Ownership transferred to destFile

                // 5. Update repository state. The old file has already been replaced atomically.
                _installationState.value = InstallationState.Installing
                val result = repository.updateExtension(pkgName, destFile.absolutePath)
                result.onSuccess { ext ->
                    _installationState.value = InstallationState.Success(ext)
                    ExtensionInstallReceiver.notifyReplaced(context, pkgName)
                }.onFailure { error ->
                    _installationState.value = InstallationState.Error(
                        "Failed to update extension: ${error.message}", error
                    )
                }
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _installationState.value = InstallationState.Error("Update failed", e)
                Result.failure(e)
            } finally {
                // Clean up temp file if the atomic rename never happened.
                tempFile?.delete()
                // Clean up the download artifact.
                if (newApkFile.exists() && newApkFile.parentFile == downloadsDir) newApkFile.delete()
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

            // Look the row up rather than inferring the backend from the name. Getting this
            // wrong is not cosmetic: without the branch below a JavaScript source would fall
            // through to the private-APK path, which deletes the database row and nothing else
            // — leaving the script on disk, still registered with the engine, and its stored
            // preferences intact. Those preferences routinely hold the user's login for the
            // site, so "uninstalled" would leave the credentials behind.
            if (repository.getExtension(pkgName)?.isJavaScript == true) {
                return@withContext uninstallJavaScript(pkgName)
            }

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
                File(extensionsDir, "$pkgName.ext").takeIf { it.exists() }?.delete()

                Result.success(Unit)
            } else {
                // Private/sideloaded extension: delete local APK and remove from DB.
                File(extensionsDir, "$pkgName.ext").takeIf { it.exists() }?.delete()

                // Remove from repository and notify the receiver.
                finalizeRemoval(pkgName)
            }
        } catch (e: CancellationException) {
            throw e
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
    
    private fun parseApkInfo(apkFile: File): PackageInfo? {
        return context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES or PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS
            }
        )
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
            } catch (e: CancellationException) {
                throw e
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

    private fun computeHash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
