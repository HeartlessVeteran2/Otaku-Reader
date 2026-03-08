package app.otakureader.core.extension.loader

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.io.File

/**
 * Result of loading an extension.
 */
sealed class ExtensionLoadResult {
    data class Success(
        val extension: Extension,
        val sources: List<Source>
    ) : ExtensionLoadResult()

    data class Error(val message: String, val throwable: Throwable? = null) : ExtensionLoadResult()
}

/**
 * Loads APK extensions dynamically using DexClassLoader.
 * Extracts Source classes from the extension's APK.
 *
 * Compatible with legacy Tachiyomi/Komikku extensions — extensions are identified
 * by the `tachiyomi.extension` uses-feature flag and their source class(es) are
 * declared in the `tachiyomi.extension.class` metadata entry (semicolon-separated).
 * Extensions that expose a SourceFactory via `tachiyomi.extension.factory` are also
 * supported.
 */
class ExtensionLoader(
    private val context: Context
) {

    companion object {
        /** Feature flag that identifies a package as a Tachiyomi-compatible extension. */
        const val EXTENSION_FEATURE = "tachiyomi.extension"

        /** Metadata key containing the fully-qualified source class name(s). */
        const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"

        /** Metadata key for extensions that use SourceFactory. */
        const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"

        /** Metadata key indicating NSFW content (1 = nsfw). */
        const val METADATA_NSFW = "tachiyomi.extension.nsfw"

        /** Minimum supported extension library version. */
        const val LIB_VERSION_MIN = 1.2

        /** Maximum supported extension library version. */
        const val LIB_VERSION_MAX = 1.5

        private const val DEX_OUTPUT_DIR = "extension_dex"

        @Suppress("DEPRECATION")
        val PACKAGE_FLAGS: Int = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
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
            packageInfo.applicationInfo?.fixBasePaths(apkPath)

            loadFromPackageInfo(packageInfo)
        } catch (e: Exception) {
            ExtensionLoadResult.Error("Failed to load extension: ${e.message}", e)
        }
    }

    /**
     * Load an already-installed extension by package name.
     * @param pkgName Android package name of the installed extension
     * @return [ExtensionLoadResult] for the installed extension
     */
    fun loadExtensionFromPkgName(pkgName: String): ExtensionLoadResult {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    pkgName,
                    PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
            }
            loadFromPackageInfo(packageInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            ExtensionLoadResult.Error("Package not found: $pkgName", e)
        } catch (e: Exception) {
            ExtensionLoadResult.Error("Failed to load extension: ${e.message}", e)
        }
    }

    /**
     * Load all installed packages that declare the Tachiyomi extension feature flag.
     * @return List of successfully loaded extensions
     */
    fun loadAllExtensions(): List<ExtensionLoadResult.Success> {
        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        return installedPkgs
            .filter { isPackageAnExtension(it) }
            .mapNotNull { pkgInfo ->
                loadFromPackageInfo(pkgInfo) as? ExtensionLoadResult.Success
            }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Core loading logic shared between APK-path and package-name entry points. */
    private fun loadFromPackageInfo(packageInfo: PackageInfo): ExtensionLoadResult {
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

        val isNsfw = (appInfo.metaData?.getInt(METADATA_NSFW) ?: 0) == 1

        // Build a DexClassLoader for dynamic class loading
        val apkPath = appInfo.sourceDir
        val nativeLibDir = appInfo.nativeLibraryDir
        val dexOutputDir = File(context.codeCacheDir, DEX_OUTPUT_DIR).apply { mkdirs() }
        val classLoader = DexClassLoader(
            apkPath,
            dexOutputDir.absolutePath,
            nativeLibDir,
            context.classLoader,
        )

        // Resolve source instances from the metadata
        val sources = resolveSourcesFromMetadata(appInfo, pkgName, classLoader)
            .ifEmpty { return ExtensionLoadResult.Error("No valid sources found in extension $pkgName") }

        val extension = buildExtension(apkPath, packageInfo, sources, isNsfw)
        return ExtensionLoadResult.Success(extension, sources)
    }

    /**
     * Returns true if the given package declares the Tachiyomi extension uses-feature.
     */
    fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /**
     * Resolve source instances from the extension's metadata.
     *
     * First checks [METADATA_SOURCE_FACTORY]; if absent, falls back to
     * [METADATA_SOURCE_CLASS]. Both keys support multiple class names separated by `;`.
     * Relative class names starting with `.` are expanded using the package name.
     *
     * Loaded objects are cast to [eu.kanade.tachiyomi.source.Source] / [SourceFactory]
     * so they match the ABI that real Tachiyomi/Komikku extensions implement.
     */
    private fun resolveSourcesFromMetadata(
        appInfo: ApplicationInfo,
        pkgName: String,
        classLoader: DexClassLoader,
    ): List<Source> {
        val metadata = appInfo.metaData ?: return emptyList()

        // Prefer SourceFactory when declared
        val factoryClassName = metadata.getString(METADATA_SOURCE_FACTORY)
        if (!factoryClassName.isNullOrBlank()) {
            val resolvedClass = resolveClassName(factoryClassName.trim(), pkgName)
            val instance = instantiateClass(classLoader, resolvedClass)
            if (instance is SourceFactory) {
                return instance.createSources()
            }
        }

        // Fall back to direct source class(es)
        val sourceClassEntry = metadata.getString(METADATA_SOURCE_CLASS)
            ?: return emptyList()

        return sourceClassEntry
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { rawClass ->
                val resolvedClass = resolveClassName(rawClass, pkgName)
                when (val instance = instantiateClass(classLoader, resolvedClass)) {
                    is SourceFactory -> instance.createSources()
                    is Source -> listOf(instance)
                    else -> emptyList()
                }
            }
    }

    /**
     * Expand a potentially relative class name (starting with `.`) using the package name.
     */
    private fun resolveClassName(className: String, pkgName: String): String {
        return if (className.startsWith(".")) pkgName + className else className
    }

    /**
     * Instantiate a class by name using the provided class loader, returning null on any error.
     */
    private fun instantiateClass(classLoader: DexClassLoader, className: String): Any? {
        return try {
            Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Build the [Extension] domain model from the loaded package data.
     */
    private fun buildExtension(
        apkPath: String,
        packageInfo: PackageInfo,
        sources: List<Source>,
        isNsfw: Boolean,
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

    /**
     * On Android 13+ the [ApplicationInfo] produced by [PackageManager.getPackageArchiveInfo]
     * may have null [ApplicationInfo.sourceDir], which breaks class loading and icon loading.
     */
    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) sourceDir = apkPath
        if (publicSourceDir == null) publicSourceDir = apkPath
    }
}
