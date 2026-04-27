package app.otakureader.core.extension.loader

import android.content.Context
import android.content.pm.PackageInfo
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import java.io.File

/**
 * Result of loading an extension.
 *
 * Matches Komikku's LoadResult hierarchy: Success, Untrusted, and Error.
 */
sealed class ExtensionLoadResult {
    data class Success(
        val extension: Extension,
        val sources: List<Source>,
    ) : ExtensionLoadResult()

    /**
     * Extension loaded but its signature is not in the trusted set.
     * The user must explicitly trust it before it can be used — matches Komikku.
     */
    data class Untrusted(val extension: Extension) : ExtensionLoadResult()

    data class Error(val message: String, val throwable: Throwable? = null) : ExtensionLoadResult()
}

/**
 * Thin orchestrator that loads APK extensions for use by the app.
 *
 * Compatible with Tachiyomi/Komikku extensions — extensions are identified by the
 * `tachiyomi.extension` uses-feature flag and their source class(es) are declared in
 * the `tachiyomi.extension.class` metadata entry (semicolon-separated). Extensions
 * that expose a `SourceFactory` via `tachiyomi.extension.factory` are also supported.
 *
 * The actual heavy lifting is delegated to three focused, independently
 * unit-testable collaborators:
 *
 *  - [ExtensionApkParser] — reads package metadata via [android.content.pm.PackageManager]
 *  - [ExtensionSignatureVerifier] — computes signature hashes and consults [TrustedSignatureStore]
 *  - [ExtensionClassLoaderFactory] — builds [ChildFirstPathClassLoader]s for each APK
 *
 * Supports two kinds of extensions (matching Komikku):
 * 1. **Shared extensions** – installed via the system package installer and available
 *    to all Tachiyomi-compatible apps.
 * 2. **Private extensions** – stored in [getPrivateExtensionDir] (`filesDir/exts/`)
 *    with the `.ext` file extension; only accessible by this app.
 */
class ExtensionLoader(
    private val context: Context,
    private val apkParser: ExtensionApkParser,
    private val signatureVerifier: ExtensionSignatureVerifier,
    private val classLoaderFactory: ExtensionClassLoaderFactory,
) {

    /**
     * Convenience constructor preserved for production wiring (Hilt module).
     * Builds the collaborators from the supplied [Context] and [TrustedSignatureStore].
     */
    constructor(
        context: Context,
        trustedSignatureStore: TrustedSignatureStore,
    ) : this(
        context = context,
        apkParser = ExtensionApkParser(context),
        signatureVerifier = ExtensionSignatureVerifier(trustedSignatureStore),
        classLoaderFactory = ExtensionClassLoaderFactory(),
    )

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

        /** Package flags required to load extensions (signatures + metadata). */
        val PACKAGE_FLAGS: Int = ExtensionApkParser.PACKAGE_FLAGS

        /** Directory where private extensions are stored (matches Komikku's `filesDir/exts`). */
        fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "exts")
    }

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

            val packageInfo = apkParser.parseApk(apkPath)
                ?: return ExtensionLoadResult.Error("Failed to parse package info from APK")

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
        val extension = apkParser.parseApk(file.absolutePath)
            ?.takeIf { apkParser.isPackageAnExtension(it) }
            ?: return false

        val current = getPrivateExtensionPackageInfo(extension.packageName)
        if (current != null) {
            val currentVersion = apkParser.getVersionCode(current)
            val newVersion = apkParser.getVersionCode(extension)
            if (newVersion < currentVersion) return false

            // Signature must match existing private extension
            val existingHash = signatureVerifier.getSignatureHash(current)
            val newHash = signatureVerifier.getSignatureHash(extension)
            if (existingHash != null && newHash != existingHash) return false
        }

        val privateDir = apkParser.getPrivateExtensionDir()
        if (!privateDir.exists() && !privateDir.mkdirs()) {
            return false
        }
        if (!privateDir.isDirectory) {
            return false
        }

        val target = File(
            privateDir,
            "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION",
        )
        return try {
            target.delete()
            file.copyTo(target, overwrite = true)
            target.setReadOnly()
            // Auto-trust private extensions — their signature was already verified above.
            signatureVerifier.getSignatureHash(extension)?.let { signatureVerifier.trust(it) }
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
            apkParser.getPrivateExtensionDir(),
            "$pkgName.$PRIVATE_EXTENSION_EXTENSION",
        ).delete()
    }

    /**
     * Load an already-installed extension by package name.
     * Checks private extensions first; falls back to shared (system) extension.
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
     */
    fun loadAllExtensions(): List<ExtensionLoadResult> {
        val sharedExtPkgs = apkParser.getInstalledPackages()
            .asSequence()
            .filter { apkParser.isPackageAnExtension(it) }
            .map { ExtensionInfo(it, isShared = true) }

        val privateExtPkgs = apkParser.getPrivateExtensionDir()
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            .onEach { if (it.canWrite()) it.setReadOnly() }
            .mapNotNull { file -> apkParser.parseApk(file.absolutePath) }
            .filter { apkParser.isPackageAnExtension(it) }
            .map { ExtensionInfo(it, isShared = false) }

        // Merge: for duplicate package names pick the higher version code
        val merged = (sharedExtPkgs + privateExtPkgs)
            .groupBy { it.packageInfo.packageName }
            .values
            .mapNotNull { entries ->
                entries.maxByOrNull { apkParser.getVersionCode(it.packageInfo) }
            }

        return merged.map { loadFromPackageInfo(it.packageInfo, it.isShared) }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private data class ExtensionInfo(val packageInfo: PackageInfo, val isShared: Boolean)

    private fun getExtensionInfoFromPkgName(pkgName: String): ExtensionInfo? {
        val privatePkg = getPrivateExtensionPackageInfo(pkgName)
            ?.takeIf { apkParser.isPackageAnExtension(it) }
            ?.let { ExtensionInfo(it, isShared = false) }

        val sharedPkg = apkParser.getInstalledPackage(pkgName)
            ?.takeIf { apkParser.isPackageAnExtension(it) }
            ?.let { ExtensionInfo(it, isShared = true) }

        return when {
            privatePkg == null -> sharedPkg
            sharedPkg == null -> privatePkg
            else -> {
                val pv = apkParser.getVersionCode(privatePkg.packageInfo)
                val sv = apkParser.getVersionCode(sharedPkg.packageInfo)
                if (pv >= sv) privatePkg else sharedPkg
            }
        }
    }

    private fun getPrivateExtensionPackageInfo(pkgName: String): PackageInfo? {
        val file = File(
            apkParser.getPrivateExtensionDir(),
            "$pkgName.$PRIVATE_EXTENSION_EXTENSION",
        )
        return if (file.isFile) apkParser.parseApk(file.absolutePath) else null
    }

    /** Core loading logic shared between APK-path and package-name entry points. */
    private fun loadFromPackageInfo(packageInfo: PackageInfo, isShared: Boolean): ExtensionLoadResult {
        // Must declare the Tachiyomi extension feature flag
        if (!apkParser.isPackageAnExtension(packageInfo)) {
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

        val isNsfw = apkParser.isNsfw(appInfo)

        // Build a ChildFirstPathClassLoader for dynamic class loading (matches Komikku)
        val apkPath = appInfo.sourceDir
            ?: return ExtensionLoadResult.Error("Application sourceDir is null for package $pkgName")
        val nativeLibDir = appInfo.nativeLibraryDir

        val classLoader = try {
            classLoaderFactory.create(
                apkPath = apkPath,
                nativeLibDir = nativeLibDir,
                parentClassLoader = context.classLoader,
            )
        } catch (e: IllegalArgumentException) {
            return ExtensionLoadResult.Error("Invalid parameters for class loader: ${e.message}", e)
        }

        // Resolve source instances from the metadata
        val sources = ExtensionLoadingUtils.resolveSourcesFromMetadata(appInfo, pkgName, classLoader)
            .ifEmpty { return ExtensionLoadResult.Error("No valid sources found in extension $pkgName") }

        val extension = buildExtension(apkPath, packageInfo, sources, isNsfw, isShared)

        // Signature trust check: private extensions are verified at install time; shared
        // extensions (installed via system package manager) must be in the user-approved set.
        // Fail closed: if the signature hash cannot be computed, treat the extension as untrusted.
        val sigHash = extension.signatureHash
        if (isShared && (sigHash == null || !signatureVerifier.isTrusted(sigHash))) {
            return ExtensionLoadResult.Untrusted(extension)
        }

        return ExtensionLoadResult.Success(extension, sources)
    }

    /**
     * Permanently trust an extension by its signature hash so future loads return [ExtensionLoadResult.Success].
     */
    fun trustExtension(signatureHash: String) {
        signatureVerifier.trust(signatureHash)
    }

    /**
     * Revoke trust for an extension signature — future loads will return [ExtensionLoadResult.Untrusted].
     */
    fun revokeExtensionTrust(signatureHash: String) {
        signatureVerifier.revoke(signatureHash)
    }

    /**
     * Returns true if the given package declares the Tachiyomi extension uses-feature.
     */
    fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean = apkParser.isPackageAnExtension(pkgInfo)

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
            name = appInfo?.let { apkParser.loadLabel(it) } ?: packageInfo.packageName,
            versionCode = apkParser.getVersionCodeInt(packageInfo),
            versionName = packageInfo.versionName ?: "unknown",
            sources = sources.map { it.toExtensionSource() },
            status = InstallStatus.INSTALLED,
            apkPath = apkPath,
            iconUrl = null,
            lang = lang,
            isNsfw = isNsfw,
            installDate = System.currentTimeMillis(),
            signatureHash = signatureVerifier.getSignatureHash(packageInfo),
            isShared = isShared,
        )
    }

    /** Generate a stable numeric extension ID from its package name. */
    private fun generateExtensionId(pkgName: String): Long {
        return pkgName.hashCode().toLong().and(0xFFFFFFFFL)
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
