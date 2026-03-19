package app.otakureader.core.extension.loader

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.io.File

/**
 * Result of loading an extension.
 *
 * Matches Komikku's LoadResult hierarchy: Success, Untrusted, and Error.
 */
sealed class ExtensionLoadResult {
    data class Success(
        val extension: Extension,
        val sources: List<Source>
    ) : ExtensionLoadResult()

    /**
     * Extension loaded but its signature is not in the trusted set.
     * The user must explicitly trust it before it can be used — matches Komikku.
     */
    data class Untrusted(val extension: Extension) : ExtensionLoadResult()

    data class Error(val message: String, val throwable: Throwable? = null) : ExtensionLoadResult()
}

/**
 * Loads APK extensions using [ChildFirstPathClassLoader].
 * Extracts Source classes from the extension's APK.
 *
 * Compatible with Tachiyomi/Komikku extensions — extensions are identified
 * by the `tachiyomi.extension` uses-feature flag and their source class(es) are
 * declared in the `tachiyomi.extension.class` metadata entry (semicolon-separated).
 * Extensions that expose a SourceFactory via `tachiyomi.extension.factory` are also
 * supported.
 *
 * Supports two kinds of extensions (matching Komikku):
 * 1. **Shared extensions** – installed via the system package installer and available
 *    to all Tachiyomi-compatible apps.
 * 2. **Private extensions** – stored in [getPrivateExtensionDir] (`filesDir/exts/`)
 *    with the `.ext` file extension; only accessible by this app.
 */
class ExtensionLoader(
    private val context: Context
) {

    companion object {
        /** Feature flag that identifies a package as a Tachiyomi-compatible extension. */
        const val EXTENSION_FEATURE = ExtensionLoadingUtils.EXTENSION_FEATURE

        /** Metadata key containing the fully-qualified source class name(s). */
        const val METADATA_SOURCE_CLASS = ExtensionLoadingUtils.METADATA_SOURCE_CLASS

        /** Metadata key for extensions that use SourceFactory. */
        const val METADATA_SOURCE_FACTORY = ExtensionLoadingUtils.METADATA_SOURCE_FACTORY

        /** Metadata key indicating NSFW content (1 = nsfw). */
        const val METADATA_NSFW = ExtensionLoadingUtils.METADATA_NSFW

        /**
         * Minimum supported extension library version.
         * Matches Komikku: 1.4 (was previously 1.2).
         */
        const val LIB_VERSION_MIN = 1.4

        /** Maximum supported extension library version. */
        const val LIB_VERSION_MAX = 1.5

        /** File extension for private extensions stored in [getPrivateExtensionDir]. */
        private const val PRIVATE_EXTENSION_EXTENSION = "ext"

        @Suppress("DEPRECATION")
        val PACKAGE_FLAGS: Int = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

        /** Directory where private extensions are stored (matches Komikku's `filesDir/exts`). */
        fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "exts")
    }

    private val packageManager: PackageManager = context.packageManager

    /**
     * Load an extension from its APK file path.
     * @param apkPath Path to the extension APK (installed or uninstalled)
     * @return [ExtensionLoadResult] containing the loaded extension info and sources
     */
    fun loadExtension(apkPath: String): ExtensionLoadResult {
        return try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                return ExtensionLoadResult.Error("APK file not found: $apkPath")
            }

            // Parse package info from the APK file
            val packageInfo = packageManager.getPackageArchiveInfo(apkPath, PACKAGE_FLAGS)
                ?: return ExtensionLoadResult.Error("Failed to parse package info from APK")

            // Fix base paths so assets/icon loading works on Android 13+
            packageInfo.applicationInfo?.let { ExtensionLoadingUtils.run { it.fixBasePaths(apkPath) } }

            loadFromPackageInfo(packageInfo, isShared = false)
        } catch (e: Exception) {
            ExtensionLoadResult.Error("Failed to load extension: ${e.message}", e)
        }
    }

    /**
     * Install an extension APK file to the private extension directory.
     * Validates the extension and copies it to [getPrivateExtensionDir].
     * @return true if successfully installed, false otherwise
     */
    fun installPrivateExtensionFile(file: File): Boolean {
        val extension = packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
            ?.takeIf { isPackageAnExtension(it) } ?: return false

        extension.applicationInfo?.fixBasePaths(file.absolutePath)

        val current = getPrivateExtensionPackageInfo(extension.packageName)
        if (current != null) {
            val currentVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                current.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                current.versionCode.toLong()
            }
            val newVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                extension.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                extension.versionCode.toLong()
            }
            if (newVersion < currentVersion) return false

            // Signature must match existing private extension
            val existingHash = getSignatureHash(current)
            val newHash = getSignatureHash(extension)
            if (existingHash != null && newHash != existingHash) return false
        }

        val privateDir = getPrivateExtensionDir(context)
        if (!privateDir.exists() && !privateDir.mkdirs()) {
            return false
        }
        if (!privateDir.isDirectory) {
            return false
        }

        val target = File(
            privateDir,
            "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION"
        )
        return try {
            target.delete()
            file.copyTo(target, overwrite = true)
            target.setReadOnly()
            true
        } catch (e: Exception) {
            target.delete()
            false
        }
    }

    /**
     * Remove a private extension by package name.
     */
    fun uninstallPrivateExtension(pkgName: String) {
        File(
            getPrivateExtensionDir(context),
            "$pkgName.$PRIVATE_EXTENSION_EXTENSION"
        ).delete()
    }

    /**
     * Load an already-installed extension by package name.
     * Checks private extensions first; falls back to shared (system) extension.
     * @param pkgName Android package name of the installed extension
     * @return [ExtensionLoadResult] for the installed extension
     */
    fun loadExtensionFromPkgName(pkgName: String): ExtensionLoadResult {
        return try {
            val extensionInfo = getExtensionInfoFromPkgName(pkgName)
                ?: return ExtensionLoadResult.Error("Package not found: $pkgName")
            loadFromPackageInfo(extensionInfo.packageInfo, extensionInfo.isShared)
        } catch (e: Exception) {
            ExtensionLoadResult.Error("Failed to load extension: ${e.message}", e)
        }
    }

    /**
     * Load all installed extensions (shared + private).
     *
     * When both a shared and a private extension exist for the same package name,
     * the one with the higher version code wins — matching Komikku's behaviour.
     *
     * @return List of all load results (success, untrusted, or error per extension)
     */
    fun loadAllExtensions(): List<ExtensionLoadResult> {
        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(it, isShared = true) }

        val privateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            .onEach { if (it.canWrite()) it.setReadOnly() }
            .mapNotNull { file ->
                packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
                    ?.also { it.applicationInfo?.fixBasePaths(file.absolutePath) }
            }
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(it, isShared = false) }

        // Merge: for duplicate package names pick the higher version code
        val merged = (sharedExtPkgs + privateExtPkgs)
            .groupBy { it.packageInfo.packageName }
            .values
            .mapNotNull { entries ->
                entries.maxByOrNull {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        it.packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        it.packageInfo.versionCode.toLong()
                    }
                }
            }

        return merged.map { loadFromPackageInfo(it.packageInfo, it.isShared) }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private data class ExtensionInfo(val packageInfo: PackageInfo, val isShared: Boolean)

    private fun getExtensionInfoFromPkgName(pkgName: String): ExtensionInfo? {
        val privateFile = File(
            getPrivateExtensionDir(context),
            "$pkgName.$PRIVATE_EXTENSION_EXTENSION"
        )
        val privatePkg = if (privateFile.isFile) {
            packageManager.getPackageArchiveInfo(privateFile.absolutePath, PACKAGE_FLAGS)
                ?.takeIf { isPackageAnExtension(it) }
                ?.also { it.applicationInfo?.fixBasePaths(privateFile.absolutePath) }
                ?.let { ExtensionInfo(it, isShared = false) }
        } else {
            null
        }

        val sharedPkg = try {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    pkgName,
                    PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
            }
            if (isPackageAnExtension(pi)) ExtensionInfo(pi, isShared = true) else null
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        return when {
            privatePkg == null -> sharedPkg
            sharedPkg == null -> privatePkg
            else -> {
                val pv = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    privatePkg.packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    privatePkg.packageInfo.versionCode.toLong()
                }
                val sv = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    sharedPkg.packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    sharedPkg.packageInfo.versionCode.toLong()
                }
                if (pv >= sv) privatePkg else sharedPkg
            }
        }
    }

    private fun getPrivateExtensionPackageInfo(pkgName: String): PackageInfo? {
        val file = File(
            getPrivateExtensionDir(context),
            "$pkgName.$PRIVATE_EXTENSION_EXTENSION"
        )
        return if (file.isFile) {
            packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
                ?.also { it.applicationInfo?.fixBasePaths(file.absolutePath) }
        } else {
            null
        }
    }

    /** Core loading logic shared between APK-path and package-name entry points. */
    private fun loadFromPackageInfo(packageInfo: PackageInfo, isShared: Boolean): ExtensionLoadResult {
        // Must declare the Tachiyomi extension feature flag
        if (!isPackageAnExtension(packageInfo)) {
            return ExtensionLoadResult.Error("Not a valid Tachiyomi-compatible extension (missing feature flag)")
        }

        val appInfo = packageInfo.applicationInfo
            ?: return ExtensionLoadResult.Error("No application info in package")
        val pkgName = packageInfo.packageName
        val versionName = packageInfo.versionName

        if (versionName.isNullOrEmpty()) {
            return ExtensionLoadResult.Error("Missing versionName for extension $pkgName")
        }

        // Validate library version from the version name prefix (e.g., "1.4.x.y")
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            return ExtensionLoadResult.Error(
                "Unsupported lib version $libVersion for $pkgName (expected $LIB_VERSION_MIN..$LIB_VERSION_MAX)",
            )
        }

        val isNsfw = ExtensionLoadingUtils.isNsfw(appInfo)

        // Build a ChildFirstPathClassLoader for dynamic class loading (matches Komikku)
        val apkPath = appInfo.sourceDir
            ?: return ExtensionLoadResult.Error("Application sourceDir is null for package $pkgName")
        val nativeLibDir = appInfo.nativeLibraryDir

        val classLoader = try {
            ExtensionLoadingUtils.createClassLoader(
                apkPath,
                nativeLibDir = nativeLibDir,
                parentClassLoader = context.classLoader
            )
        } catch (e: IllegalArgumentException) {
            return ExtensionLoadResult.Error("Invalid parameters for class loader: ${e.message}", e)
        }

        // Resolve source instances from the metadata
        val sources = ExtensionLoadingUtils.resolveSourcesFromMetadata(appInfo, pkgName, classLoader)
            .ifEmpty { return ExtensionLoadResult.Error("No valid sources found in extension $pkgName") }

        val extension = buildExtension(apkPath, packageInfo, sources, isNsfw, isShared)
        return ExtensionLoadResult.Success(extension, sources)
    }

    /**
     * Returns true if the given package declares the Tachiyomi extension uses-feature.
     */
    fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return ExtensionLoadingUtils.isPackageAnExtension(pkgInfo)
    }

    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) sourceDir = apkPath
        if (publicSourceDir == null) publicSourceDir = apkPath
    }

    /**
     * Build the [Extension] domain model from the loaded package data.
     */
    private fun buildExtension(
        apkPath: String,
        packageInfo: PackageInfo,
        sources: List<Source>,
        isNsfw: Boolean,
        isShared: Boolean,
    ): Extension {
        val appInfo = packageInfo.applicationInfo
        // lang comes from CatalogueSource; plain Sources have no lang field
        val langs = sources.filterIsInstance<CatalogueSource>().map { it.lang }.toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        return Extension(
            id = generateExtensionId(packageInfo.packageName),
            pkgName = packageInfo.packageName,
            name = appInfo?.loadLabel(packageManager)?.toString()
                ?.substringAfter("Tachiyomi: ")
                ?: packageInfo.packageName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            },
            versionName = packageInfo.versionName ?: "unknown",
            sources = sources.map { it.toExtensionSource() },
            status = InstallStatus.INSTALLED,
            apkPath = apkPath,
            iconUrl = null,
            lang = lang,
            isNsfw = isNsfw,
            installDate = System.currentTimeMillis(),
            signatureHash = getSignatureHash(packageInfo),
            isShared = isShared,
        )
    }

    /** Generate a stable numeric extension ID from its package name. */
    private fun generateExtensionId(pkgName: String): Long {
        return pkgName.hashCode().toLong().and(0xFFFFFFFFL)
    }

    /** Return a hex-encoded SHA-256 of the first signing certificate, or null. */
    private fun getSignatureHash(packageInfo: PackageInfo): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo ?: return null
                val cert = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners?.firstOrNull()
                } else {
                    signingInfo.signingCertificateHistory?.firstOrNull()
                }
                cert?.toByteArray()?.let { sha256Hex(it) }
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.firstOrNull()?.toByteArray()?.let { sha256Hex(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /** Convert a loaded [Source] to the [ExtensionSource] domain model. */
    private fun Source.toExtensionSource(): ExtensionSource {
        val catalogue = this as? CatalogueSource
        return ExtensionSource(
            id = this.id,
            name = this.name,
            lang = catalogue?.lang ?: "",
            baseUrl = catalogue?.baseUrl ?: "",
            supportsSearch = true,
            supportsLatest = catalogue?.supportsLatest ?: false,
        )
    }
}
