package app.otakureader.core.extension.loader

import android.content.Context
import android.content.pm.PackageInfo
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
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

    data class Error(
        val message: String,
        val throwable: Throwable? = null,
        val reason: Reason = Reason.UNKNOWN,
    ) : ExtensionLoadResult() {

        /**
         * Machine-readable classification of a load failure.
         *
         * [message] stays human-facing and may be reworded freely; anything that needs to
         * *branch* on a failure — tests, log grouping, UI — must use this instead. Matching
         * on message substrings is silently fragile: a test asserting a message does NOT
         * contain some phrase keeps passing after the phrase is reworded, so the guard
         * evaporates without any test turning red.
         */
        enum class Reason {
            /** APK file missing at the given path. */
            APK_NOT_FOUND,

            /**
             * No installed or private package matched the requested package name.
             *
             * Distinct from [APK_NOT_FOUND]: this path is given a package name, never a file
             * path, so reporting a missing *file* would describe a lookup that never happened.
             */
            PACKAGE_NOT_FOUND,

            /** PackageManager could not parse the APK. */
            PARSE_FAILED,

            /** Package does not declare the Tachiyomi extension feature flag. */
            NOT_AN_EXTENSION,

            /** Package has no ApplicationInfo, or no usable sourceDir. */
            MISSING_APPLICATION_INFO,

            /** Package has no versionName, so the lib version cannot be derived. */
            MISSING_VERSION_NAME,

            /** Lib version outside [LIB_VERSION_MIN]..[LIB_VERSION_MAX]. */
            UNSUPPORTED_LIB_VERSION,

            /** The class loader could not be constructed for the APK. */
            CLASS_LOADER_FAILED,

            /** Loaded, but no source class could be instantiated from the manifest metadata. */
            NO_VALID_SOURCES,

            /** Anything not classified above, including unexpected exceptions. */
            UNKNOWN,
        }
    }
}

/**
 * An extensions-lib version, as the ordered `(major, minor)` pair it actually is.
 *
 * Deliberately not a `Double`. Parsing `"1.40"` or `"1.10"` as a decimal is wrong in both
 * directions, and silently so:
 *
 *  - `"1.40"` → `1.4`, indistinguishable from lib 1.4, so an unsupported extension is **admitted**
 *    and only fails later, deep in class loading;
 *  - `"1.10"` → `1.1`, which compares *below* 1.4, so a future lib 1.10 extension would be
 *    **rejected as too old** — the same misdiagnosis this class already had for `"1.7"`.
 *
 * That second case is not hypothetical: extensions-lib runs 1.7 → 1.8 → 1.9 → 1.10, so a decimal
 * comparison acquires a latent rejection bug the moment the minor version reaches double digits.
 */
data class LibVersion(val major: Int, val minor: Int) : Comparable<LibVersion> {

    override fun compareTo(other: LibVersion): Int =
        compareValuesBy(this, other, LibVersion::major, LibVersion::minor)

    override fun toString(): String = "$major.$minor"

    companion object {
        /**
         * Read the leading `major.minor` pair from an extension's `versionName`.
         *
         * Returns `null` when no such pair is present, which callers treat as unsupported.
         * Trailing components are ignored: `"1.4.19"`, `"1.4"` and `"1.4.19.1"` all yield 1.4.
         */
        fun parse(versionName: String): LibVersion? {
            val parts = versionName.split('.')
            if (parts.size < 2) return null
            val major = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val minor = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return LibVersion(major, minor)
        }
    }
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
        val LIB_VERSION_MIN = LibVersion(1, 4)

        /**
         * Maximum supported extension library version.
         *
         * This is a *compatibility gate*, not a capability check: an extension whose
         * lib version falls outside the window is rejected before any of its code runs.
         * Keeping it at 1.5 meant every Keiyoushi/Komikku extension built against
         * extensions-lib 1.6 or 1.7 was refused with "Unsupported lib version" — which
         * presented as the app simply having no sources.
         *
         * 1.7 is the correct ceiling because the host contract in `core:tachiyomi-compat`
         * already implements those revisions: see the `@since komikku/extensions-lib 1.6`
         * members on [eu.kanade.tachiyomi.source.CatalogueSource] (related-manga APIs) and
         * the 1.7 member on [eu.kanade.tachiyomi.source.Source]. The loader was rejecting
         * extensions that use APIs this app already ships.
         *
         * Widening is safe because extensions-lib revisions are additive — 1.6/1.7 added
         * members to the host interfaces without breaking the 1.4-era ones — so a 1.4
         * extension and a 1.7 extension both link against the same contract.
         *
         * Raise this again only after adding the corresponding members to
         * `core:tachiyomi-compat`. Admitting an extension built against a revision the host
         * does not implement produces a [LinkageError], and which one depends on what is
         * missing:
         *
         *  - a missing host **class** → `NoClassDefFoundError` while the extension's class is
         *    being resolved, i.e. at instantiation, so the loader catches it and reports the
         *    extension as having no valid sources;
         *  - a missing **method** on a class the host does ship → `NoSuchMethodError` at the
         *    call site. Since additive revisions are exactly this case, that is the failure to
         *    expect here — and it is the more dangerous one, because it escapes the loader
         *    entirely and surfaces later as a crash mid-browse or mid-read;
         *  - an interface member the extension does not implement but the host invokes →
         *    `AbstractMethodError`, likewise at call time.
         *
         * So the version gate is not merely a nicety: it converts a deferred, hard-to-trace
         * runtime crash into a clear, up-front rejection.
         */
        val LIB_VERSION_MAX = LibVersion(1, 7)

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
                return ExtensionLoadResult.Error(
                    "APK file not found: $apkPath",
                    reason = ExtensionLoadResult.Error.Reason.APK_NOT_FOUND,
                )
            }

            val packageInfo = apkParser.parseApk(apkPath)
                ?: return ExtensionLoadResult.Error(
                    "Failed to parse package info from APK",
                    reason = ExtensionLoadResult.Error.Reason.PARSE_FAILED,
                )

            loadFromPackageInfo(packageInfo, isShared = false)
        } catch (e: Exception) {
            ExtensionLoadResult.Error(
                "Failed to load extension: ${e.message}",
                e,
                reason = ExtensionLoadResult.Error.Reason.UNKNOWN,
            )
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
                ?: return ExtensionLoadResult.Error(
                    "Package not found: $pkgName",
                    reason = ExtensionLoadResult.Error.Reason.PACKAGE_NOT_FOUND,
                )
            loadFromPackageInfo(extensionInfo.packageInfo, extensionInfo.isShared)
        } catch (e: Exception) {
            ExtensionLoadResult.Error(
                "Failed to load extension: ${e.message}",
                e,
                reason = ExtensionLoadResult.Error.Reason.UNKNOWN,
            )
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
    /**
     * Extract the extensions-lib version from an extension's `versionName`.
     *
     * The lib version is the leading `major.minor` pair: `"1.7.42"` means extensions-lib 1.7.
     *
     * Takes the first two components explicitly rather than stripping the last one. Dropping
     * the trailing component assumes exactly three parts and silently misreads anything else:
     * `"1.7"` became `"1"` → `1.0`, which is *below* [LIB_VERSION_MIN], so a valid 1.7 extension
     * was rejected as too old; `"1.4.19.1"` became `"1.4.19"`, which is not a number at all and
     * parsed to `null`, so it was rejected outright. Both failures are indistinguishable from a
     * genuinely unsupported version in the error message.
     *
     * Returns `null` when no `major.minor` pair can be read, which the caller treats as
     * unsupported.
     */
    /**
     * Check that the extension declares a lib version this host can satisfy.
     *
     * Returns the rejection to propagate, or `null` when the extension may proceed.
     */
    private fun validateLibVersion(versionName: String?, pkgName: String): ExtensionLoadResult.Error? {
        if (versionName.isNullOrEmpty()) {
            return ExtensionLoadResult.Error(
                "Missing versionName for extension $pkgName",
                reason = ExtensionLoadResult.Error.Reason.MISSING_VERSION_NAME,
            )
        }

        val libVersion = LibVersion.parse(versionName)
        if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            return ExtensionLoadResult.Error(
                "Unsupported lib version $libVersion for $pkgName (expected $LIB_VERSION_MIN..$LIB_VERSION_MAX)",
                reason = ExtensionLoadResult.Error.Reason.UNSUPPORTED_LIB_VERSION,
            )
        }

        return null
    }

    private fun loadFromPackageInfo(packageInfo: PackageInfo, isShared: Boolean): ExtensionLoadResult {
        // Must declare the Tachiyomi extension feature flag
        if (!apkParser.isPackageAnExtension(packageInfo)) {
            return ExtensionLoadResult.Error(
                "Not a valid Tachiyomi-compatible extension (missing feature flag)",
                reason = ExtensionLoadResult.Error.Reason.NOT_AN_EXTENSION,
            )
        }

        val appInfo = packageInfo.applicationInfo
            ?: return ExtensionLoadResult.Error(
                "No application info in package",
                reason = ExtensionLoadResult.Error.Reason.MISSING_APPLICATION_INFO,
            )
        val pkgName = packageInfo.packageName

        validateLibVersion(packageInfo.versionName, pkgName)?.let { return it }

        val isNsfw = apkParser.isNsfw(appInfo)

        // Build a ChildFirstPathClassLoader for dynamic class loading (matches Komikku)
        val apkPath = appInfo.sourceDir
            ?: return ExtensionLoadResult.Error(
                "Application sourceDir is null for package $pkgName",
                reason = ExtensionLoadResult.Error.Reason.MISSING_APPLICATION_INFO,
            )
        val nativeLibDir = appInfo.nativeLibraryDir

        val classLoader = try {
            classLoaderFactory.create(
                apkPath = apkPath,
                nativeLibDir = nativeLibDir,
                parentClassLoader = context.classLoader,
            )
        } catch (e: IllegalArgumentException) {
            return ExtensionLoadResult.Error(
                "Invalid parameters for class loader: ${e.message}",
                e,
                reason = ExtensionLoadResult.Error.Reason.CLASS_LOADER_FAILED,
            )
        }

        // Resolve source instances from the metadata. The resolution carries any
        // per-class failure reasons so we can surface them in the user-visible error
        // instead of just saying "no valid sources found" (the loader used to be silent
        // for LinkageError / ctor-threw / etc. — see ExtensionLoadingUtils.instantiateClass).
        val resolution = ExtensionLoadingUtils.resolveSourcesFromMetadata(appInfo, pkgName, classLoader)
        if (resolution.sources.isEmpty()) {
            val detail = if (resolution.errors.isEmpty()) {
                "no source class declared in manifest metadata"
            } else {
                resolution.errors.joinToString(separator = "; ")
            }
            return ExtensionLoadResult.Error(
                "No valid sources found in extension $pkgName ($detail)",
                reason = ExtensionLoadResult.Error.Reason.NO_VALID_SOURCES,
            )
        }
        val sources = resolution.sources

        val extension = buildExtension(apkPath, packageInfo, sources, isNsfw, isShared)

        // Signature trust check — applied to ALL extensions regardless of origin.
        // Private extensions are auto-trusted at install time via installPrivateExtensionFile(),
        // but the trust store is the authoritative gate at load time.
        // Fail closed: if the hash cannot be computed, treat the extension as untrusted.
        val sigHash = extension.signatureHash
        if (sigHash == null || !signatureVerifier.isTrusted(sigHash)) {
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
        // baseUrl only exists on HttpSource (matching Tachiyomi/Komikku); non-HTTP
        // sources (e.g. local) have no base URL.
        val httpSource = this as? HttpSource
        return ExtensionSource(
            id = this.id,
            name = this.name,
            lang = catalogue?.lang ?: "",
            baseUrl = httpSource?.baseUrl ?: "",
            supportsSearch = true,
            supportsLatest = catalogue?.supportsLatest ?: false,
        )
    }
}
