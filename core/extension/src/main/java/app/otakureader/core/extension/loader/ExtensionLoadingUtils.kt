package app.otakureader.core.extension.loader

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.util.Log
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

/**
 * Outcome of attempting to instantiate a class from an extension APK.
 *
 * Surfaces the reason on failure so the loader can include it in its user-visible error
 * message instead of just saying "no valid sources found" — without this, every cause
 * (missing class, missing no-arg ctor, constructor threw, linkage / NoClassDefFoundError,
 * static-initializer error) produced the same opaque toast.
 */
sealed class InstantiationResult {
    data class Success(val instance: Any) : InstantiationResult()

    /** Class isn't in the APK. Expected for non-source entries in a `;`-separated class list. */
    data object NotFound : InstantiationResult()

    /** Class was found but couldn't be instantiated. [reason] is a short single-line label. */
    data class Failure(val reason: String, val cause: Throwable) : InstantiationResult()
}

/**
 * Result of resolving source instances from an extension's manifest metadata.
 *
 * [errors] holds one short string per class/factory that was declared in the manifest but
 * couldn't be instantiated (in declaration order). The loader includes them in its error
 * message when [sources] ends up empty, so the user sees *why* the extension didn't load.
 */
data class SourceResolution(
    val sources: List<Source>,
    val errors: List<String>,
)

/**
 * Shared utilities for loading Tachiyomi-compatible extension APKs.
 *
 * These utilities are used by [ExtensionLoader], which is the single extension-loading
 * path in the app. A second, unreferenced loader previously existed in
 * `core:tachiyomi-compat` with its own duplicated copy of this logic; it was deleted
 * rather than kept in sync, since only this path was ever wired up.
 */
object ExtensionLoadingUtils {
    private const val TAG = "ExtensionLoadingUtils"

    /** Feature flag that identifies a package as a Tachiyomi-compatible extension. */
    const val EXTENSION_FEATURE = "tachiyomi.extension"

    /** Metadata key containing the fully-qualified source class name(s). */
    const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"

    /** Metadata key for extensions that use SourceFactory. */
    const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"

    /** Metadata key indicating NSFW content (1 = nsfw). */
    const val METADATA_NSFW = "tachiyomi.extension.nsfw"

    /**
     * Check if a PackageInfo declares the Tachiyomi extension feature flag.
     */
    fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /**
     * Create a [ChildFirstPathClassLoader] for the given APK.
     *
     * Uses [ChildFirstPathClassLoader] to match Komikku's extension loading strategy:
     * the extension's own libraries are preferred over the host app's libraries,
     * preventing class-version conflicts.
     *
     * @param apkPath Path to the APK file
     * @param nativeLibDir Directory containing native libraries (optional)
     * @param parentClassLoader Parent class loader (defaults to current class loader)
     * @return Configured [ChildFirstPathClassLoader]
     */
    fun createClassLoader(
        apkPath: String,
        nativeLibDir: String?,
        parentClassLoader: ClassLoader? = ExtensionLoadingUtils::class.java.classLoader
    ): ChildFirstPathClassLoader {
        require(apkPath.isNotBlank()) { "APK path must not be blank" }

        return ChildFirstPathClassLoader(
            apkPath,
            nativeLibDir,
            parentClassLoader ?: ClassLoader.getSystemClassLoader()
        )
    }

    /**
     * Expand a potentially relative class name (starting with `.`) using the package name.
     *
     * Examples:
     * - ".MySource" with package "com.example" -> "com.example.MySource"
     * - "com.example.MySource" with any package -> "com.example.MySource"
     */
    fun resolveClassName(className: String, pkgName: String): String {
        return if (className.startsWith(".")) pkgName + className else className
    }

    /**
     * Instantiate a class by name using the provided class loader.
     *
     * Every failure mode is captured as [InstantiationResult.Failure] with a short reason
     * label and the original [Throwable], and is also logged at warn level so it appears in
     * `adb logcat -s ExtensionLoadingUtils`. The only exception is [ClassNotFoundException]
     * which maps to [InstantiationResult.NotFound] — that's a routine outcome when walking
     * a `;`-separated class list where not every entry exists in the APK.
     *
     * @param classLoader The DexClassLoader to use for loading the class
     * @param className The fully-qualified class name to instantiate
     */
    fun instantiateClass(classLoader: ClassLoader, className: String): InstantiationResult {
        require(className.isNotBlank()) { "Class name must not be blank" }

        return try {
            val instance = Class.forName(className, false, classLoader)
                .getDeclaredConstructor()
                .newInstance()
            InstantiationResult.Success(instance)
        } catch (e: ClassNotFoundException) {
            // Routine when iterating a `;`-separated class list. Logged at debug only.
            runCatching { Log.d(TAG, "Class not found: $className") }
            InstantiationResult.NotFound
        } catch (e: NoSuchMethodException) {
            failure(className, "NoSuchMethodException (no no-arg constructor)", e)
        } catch (e: InstantiationException) {
            failure(className, "InstantiationException (abstract class or interface)", e)
        } catch (e: IllegalAccessException) {
            failure(className, "IllegalAccessException (constructor not accessible)", e)
        } catch (e: SecurityException) {
            failure(className, "SecurityException", e)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.targetException ?: e
            failure(className, "InvocationTargetException: ${cause.javaClass.simpleName}: ${cause.message}", cause)
        } catch (e: ExceptionInInitializerError) {
            val cause = e.cause ?: e
            failure(className, "ExceptionInInitializerError: ${cause.javaClass.simpleName}: ${cause.message}", cause)
        } catch (e: LinkageError) {
            // Typically NoClassDefFoundError → an extension references a class the host
            // (core/tachiyomi-compat) doesn't ship. This is the most common silent failure
            // for Komikku/Keiyoushi extensions, so surface the missing class name.
            failure(className, "LinkageError: ${e.javaClass.simpleName}: ${e.message}", e)
        } catch (e: RuntimeException) {
            // Some class loaders (or test stubs) may throw RuntimeException instead of ClassNotFoundException.
            failure(className, "RuntimeException: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun failure(className: String, reason: String, cause: Throwable): InstantiationResult.Failure {
        runCatching { Log.w(TAG, "Failed to instantiate $className — $reason", cause) }
        return InstantiationResult.Failure(reason, cause)
    }

    /**
     * Resolve Source instances from extension metadata.
     *
     * Checks METADATA_SOURCE_FACTORY first; if absent, reads class names from
     * METADATA_SOURCE_CLASS. Both keys support multiple class names separated by `;`.
     * Relative class names starting with `.` are expanded using the package name.
     *
     * The returned [SourceResolution] carries one error string per class/factory that was
     * declared but couldn't be instantiated, so the caller can include them in a
     * user-visible error when `sources` ends up empty.
     *
     * @param metadata Bundle containing extension metadata
     * @param pkgName Package name for resolving relative class names
     * @param classLoader DexClassLoader for loading classes
     * @param filterType Optional class to filter by (e.g., CatalogueSource::class.java)
     */
    fun resolveSourcesFromMetadata(
        metadata: android.os.Bundle,
        pkgName: String,
        classLoader: ClassLoader,
        filterType: Class<*>? = null
    ): SourceResolution {
        val sources = mutableListOf<Source>()
        val errors = mutableListOf<String>()

        // SourceFactory path: short-circuits the class list if it succeeds (prior behaviour).
        val factoryClassName = metadata.getString(METADATA_SOURCE_FACTORY)
        if (!factoryClassName.isNullOrBlank()) {
            val handled = tryFactoryEntry(
                resolveClassName(factoryClassName.trim(), pkgName),
                classLoader, filterType, sources, errors,
            )
            if (handled) return SourceResolution(sources, errors)
        }

        val sourceClassEntry = metadata.getString(METADATA_SOURCE_CLASS)
        if (sourceClassEntry.isNullOrBlank()) return SourceResolution(sources, errors)

        // NotFound is tolerated silently per-entry because a `;`-separated list can legitimately
        // name classes that don't exist in this particular extension. But if NO sources are
        // produced at all, those NotFounds ARE the failure — surface them so the user sees
        // "X: ClassNotFoundException" instead of the misleading "no source class declared".
        val notFound = mutableListOf<String>()
        sourceClassEntry
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { rawClass ->
                processClassEntry(
                    resolveClassName(rawClass, pkgName),
                    classLoader, filterType, sources, errors, notFound,
                )
            }

        if (sources.isEmpty()) {
            notFound.forEach { errors += "$it: ClassNotFoundException" }
        }

        return SourceResolution(sources, errors)
    }

    /**
     * Try the `tachiyomi.extension.factory` entry. Returns true when it produced sources
     * (caller short-circuits the class list, matching prior behaviour). Errors are
     * accumulated into [errors] regardless.
     */
    private fun tryFactoryEntry(
        resolvedClass: String,
        classLoader: ClassLoader,
        filterType: Class<*>?,
        sources: MutableList<Source>,
        errors: MutableList<String>,
    ): Boolean {
        when (val result = instantiateClass(classLoader, resolvedClass)) {
            is InstantiationResult.Success -> {
                val instance = result.instance
                if (instance is SourceFactory) {
                    sources += keepByType(instance.createSources(), filterType)
                    return true
                }
                errors += "$resolvedClass: declared as factory but is not a SourceFactory"
            }
            is InstantiationResult.NotFound -> errors += "$resolvedClass: ClassNotFoundException"
            is InstantiationResult.Failure -> errors += "$resolvedClass: ${result.reason}"
        }
        return false
    }

    /** Walk a single `tachiyomi.extension.class` entry; record `NotFound` so the caller can
     *  surface it only when no sources were produced at all. */
    private fun processClassEntry(
        resolvedClass: String,
        classLoader: ClassLoader,
        filterType: Class<*>?,
        sources: MutableList<Source>,
        errors: MutableList<String>,
        notFound: MutableList<String>,
    ) {
        when (val result = instantiateClass(classLoader, resolvedClass)) {
            is InstantiationResult.Success -> appendSuccess(resolvedClass, result.instance, filterType, sources, errors)
            is InstantiationResult.NotFound -> notFound += resolvedClass
            is InstantiationResult.Failure -> errors += "$resolvedClass: ${result.reason}"
        }
    }

    private fun appendSuccess(
        resolvedClass: String,
        instance: Any,
        filterType: Class<*>?,
        sources: MutableList<Source>,
        errors: MutableList<String>,
    ) {
        when (instance) {
            is SourceFactory -> sources += keepByType(instance.createSources(), filterType)
            is Source -> if (filterType == null || filterType.isInstance(instance)) {
                sources += instance
            } else {
                val instanceName = instance.javaClass.simpleName
                errors += "$resolvedClass: $instanceName does not match required type ${filterType.simpleName}"
            }
            else -> {
                val instanceName = instance.javaClass.simpleName
                errors += "$resolvedClass: instantiated to $instanceName, not a Source or SourceFactory"
            }
        }
    }

    private fun keepByType(sources: List<Source>, filterType: Class<*>?): List<Source> =
        if (filterType == null) sources else sources.filter { filterType.isInstance(it) }

    /**
     * Resolve Source instances from ApplicationInfo metadata.
     *
     * This is a convenience overload that extracts metadata from ApplicationInfo.
     *
     * @param appInfo ApplicationInfo containing extension metadata
     * @param pkgName Package name for resolving relative class names
     * @param classLoader DexClassLoader for loading classes
     * @param filterType Optional class to filter by (e.g., CatalogueSource::class.java)
     */
    fun resolveSourcesFromMetadata(
        appInfo: ApplicationInfo,
        pkgName: String,
        classLoader: ClassLoader,
        filterType: Class<*>? = null
    ): SourceResolution {
        val metadata = appInfo.metaData ?: return SourceResolution(emptyList(), emptyList())
        return resolveSourcesFromMetadata(metadata, pkgName, classLoader, filterType)
    }

    /**
     * Read NSFW flag from ApplicationInfo metadata.
     *
     * @return true if METADATA_NSFW is set to 1
     */
    fun isNsfw(appInfo: ApplicationInfo): Boolean {
        return (appInfo.metaData?.getInt(METADATA_NSFW) ?: 0) == 1
    }

    /**
     * Fix ApplicationInfo paths for Android 13+.
     *
     * On Android 13+, ApplicationInfo from getPackageArchiveInfo may have null
     * sourceDir/publicSourceDir, which breaks class loading and icon loading.
     */
    fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) sourceDir = apkPath
        if (publicSourceDir == null) publicSourceDir = apkPath
    }
}
