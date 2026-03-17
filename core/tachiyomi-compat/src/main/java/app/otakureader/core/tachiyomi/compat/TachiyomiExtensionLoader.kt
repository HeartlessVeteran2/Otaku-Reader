package app.otakureader.core.tachiyomi.compat

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.io.File

/**
 * Loads Tachiyomi-compatible extension APKs and instantiates their Source classes.
 *
 * Extensions are identified by the `tachiyomi.extension` uses-feature flag.  Source
 * class(es) are read from the `tachiyomi.extension.class` metadata entry
 * (semicolon-separated, relative names starting with `.` are expanded using the
 * package name).  Extensions that expose a [SourceFactory] via the
 * `tachiyomi.extension.factory` metadata key are also fully supported.
 *
 * This matches the loading strategy used by the canonical Komikku / Tachiyomi
 * repositories.
 *
 * ## Code Duplication Note
 *
 * This class maintains its own implementation of extension loading logic rather than
 * using ExtensionLoadingUtils from core:extension to avoid circular dependencies:
 * - core:extension already depends on core:tachiyomi-compat
 * - Adding the reverse dependency creates a Gradle circular dependency error
 *
 * Constants are intentionally duplicated and should be kept synchronized.
 * See docs/EXTENSION_LOADER_CONSOLIDATION.md for full details.
 */
class TachiyomiExtensionLoader(
    private val packageManager: PackageManager,
    private val cacheDir: File
) {

    companion object {
        /** Uses-feature flag that identifies a Tachiyomi-compatible extension package. */
        const val TACHIYOMI_EXTENSION_FEATURE = "tachiyomi.extension"

        /** Metadata key containing the source class name(s) (semicolon-separated). */
        const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"

        /** Metadata key for extensions that expose a SourceFactory. */
        const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"

        /** Metadata key indicating NSFW content (integer 1 = nsfw). */
        const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    }

    private val loadedExtensions = mutableMapOf<String, LoadedExtension>()

    /**
     * Data class representing a loaded extension.
     */
    data class LoadedExtension(
        val packageName: String,
        val name: String,
        val versionName: String,
        val versionCode: Long,
        val lang: String,
        val isNsfw: Boolean,
        val sources: List<TachiyomiSourceAdapter>,
        val apkPath: String,
        val classLoader: DexClassLoader,
    )

    /**
     * Load all installed Tachiyomi extensions.
     */
    fun loadAllExtensions(): List<LoadedExtension> {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }

        return installedPackages.mapNotNull { loadExtension(it) }
    }

    /**
     * Load a specific installed extension by package name.
     */
    fun loadExtension(packageName: String): LoadedExtension? {
        return try {
            val flags = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, flags)
            }
            loadExtension(packageInfo)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load an extension from an APK file path (e.g. a private/sideloaded extension).
     */
    fun loadExtensionFromApk(apkPath: String): LoadedExtension? {
        return try {
            val flags = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS
            val packageInfo = packageManager.getPackageArchiveInfo(apkPath, flags) ?: return null

            // On Android 13+ getPackageArchiveInfo does not populate sourceDir.
            packageInfo.applicationInfo?.let { ai ->
                if (ai.sourceDir == null) ai.sourceDir = apkPath
                if (ai.publicSourceDir == null) ai.publicSourceDir = apkPath
            }

            loadExtension(packageInfo)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Core loading logic for a [PackageInfo].
     *
     * Validates the extension feature flag, reads the source class name(s) from
     * the `tachiyomi.extension.class` (or `tachiyomi.extension.factory`) metadata,
     * builds a [DexClassLoader], and instantiates the sources.
     */
    private fun loadExtension(packageInfo: PackageInfo): LoadedExtension? {
        val packageName = packageInfo.packageName

        // Return cached instance if already loaded
        loadedExtensions[packageName]?.let { return it }

        // Must declare the Tachiyomi extension feature flag
        val hasFeature = packageInfo.reqFeatures?.any { it.name == TACHIYOMI_EXTENSION_FEATURE } == true
        if (!hasFeature) return null

        val appInfo = packageInfo.applicationInfo ?: return null
        val apkPath = appInfo.sourceDir ?: return null
        val metadata = appInfo.metaData ?: return null

        val isNsfw = (metadata.getInt(METADATA_NSFW, 0)) == 1

        // Build the class loader
        val nativeLibDir = appInfo.nativeLibraryDir
        val optimizedDir = File(cacheDir, "tachiyomi-dex")

        // Ensure the optimized directory exists and is usable
        if (!optimizedDir.exists() && !optimizedDir.mkdirs()) {
            // Failed to create directory - cannot proceed with class loading
            return null
        }

        // Validate that the path is actually a directory and is writable
        if (!optimizedDir.isDirectory || !optimizedDir.canWrite()) {
            // Directory is not usable - cannot proceed with class loading
            return null
        }

        val classLoader = DexClassLoader(
            apkPath,
            optimizedDir.absolutePath,
            nativeLibDir,
            TachiyomiExtensionLoader::class.java.classLoader,
        )

        // Resolve source instances
        val sources = resolveSourcesFromMetadata(metadata, packageName, classLoader)

        if (sources.isEmpty()) {
            // No valid sources found — discard the class loader and skip this extension
            return null
        }

        val langs = sources.map { it.lang }.toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = LoadedExtension(
            packageName = packageName,
            name = appInfo.loadLabel(packageManager).toString().substringAfter("Tachiyomi: "),
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            lang = lang,
            isNsfw = isNsfw,
            sources = sources.map { TachiyomiSourceAdapter(it, isNsfw) },
            apkPath = apkPath,
            classLoader = classLoader,
        )

        loadedExtensions[packageName] = extension
        return extension
    }

    /**
     * Resolve [CatalogueSource] instances from extension metadata.
     *
     * Checks [METADATA_SOURCE_FACTORY] first; if absent, reads class names from
     * [METADATA_SOURCE_CLASS].  Class names starting with `.` are expanded using
     * the package name.
     */
    private fun resolveSourcesFromMetadata(
        metadata: android.os.Bundle,
        pkgName: String,
        classLoader: DexClassLoader,
    ): List<CatalogueSource> {
        // SourceFactory path
        val factoryClass = metadata.getString(METADATA_SOURCE_FACTORY)
        if (!factoryClass.isNullOrBlank()) {
            val resolved = resolveClassName(factoryClass.trim(), pkgName)
            val factory = instantiateClass(classLoader, resolved)
            if (factory is SourceFactory) {
                return factory.createSources()
                    .filterIsInstance<CatalogueSource>()
            }
        }

        // Direct source class(es)
        val sourceClassEntry = metadata.getString(METADATA_SOURCE_CLASS) ?: return emptyList()

        return sourceClassEntry
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { rawClass ->
                val resolved = resolveClassName(rawClass, pkgName)
                when (val instance = instantiateClass(classLoader, resolved)) {
                    is SourceFactory -> instance.createSources().filterIsInstance<CatalogueSource>()
                    is CatalogueSource -> listOf(instance)
                    is Source -> emptyList() // non-catalogue sources not supported here
                    else -> emptyList()
                }
            }
    }

    /** Expand a relative class name (starting with `.`) using the package name. */
    private fun resolveClassName(className: String, pkgName: String): String {
        return if (className.startsWith(".")) pkgName + className else className
    }

    /** Instantiate a class by name; returns null on any error. */
    private fun instantiateClass(classLoader: DexClassLoader, className: String): Any? {
        if (className.isBlank()) return null

        return try {
            Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()
        } catch (e: ClassNotFoundException) {
            // Class not found in APK - expected for invalid/missing source classes
            null
        } catch (e: NoSuchMethodException) {
            // No parameterless constructor - expected for non-source classes
            null
        } catch (e: InstantiationException) {
            // Cannot instantiate (abstract class/interface) - expected for invalid sources
            null
        } catch (e: IllegalAccessException) {
            // Constructor not accessible - expected for inaccessible classes
            null
        } catch (e: SecurityException) {
            // Security manager denies access - rare but expected
            null
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // Constructor threw an exception - expected for extension code with initialization errors
            null
        } catch (e: ExceptionInInitializerError) {
            // Static initializer threw an exception - expected for extension code with init errors
            null
        } catch (e: LinkageError) {
            // Class linking failed - expected for extensions with missing dependencies
            null
        }
    }

    /**
     * Reload an extension (unload then re-load).
     */
    fun reloadExtension(packageName: String): LoadedExtension? {
        unloadExtension(packageName)
        return loadExtension(packageName)
    }

    /**
     * Unload an extension and release its class-loader resources.
     *
     * Note: [DexClassLoader] does not implement [java.io.Closeable], so there is no
     * explicit close call here.  The GC will reclaim the loader when there are no more
     * references.
     */
    fun unloadExtension(packageName: String) {
        loadedExtensions.remove(packageName)
    }

    /**
     * Unload all extensions.
     */
    fun unloadAllExtensions() {
        loadedExtensions.keys.toList().forEach { unloadExtension(it) }
    }

    /**
     * Return all currently loaded extensions.
     */
    fun getLoadedExtensions(): List<LoadedExtension> = loadedExtensions.values.toList()

    /**
     * Return whether an extension is currently loaded.
     */
    fun isExtensionLoaded(packageName: String): Boolean = loadedExtensions.containsKey(packageName)

    /**
     * Return all loaded sources from every loaded extension.
     */
    fun getAllSources(): List<TachiyomiSourceAdapter> = loadedExtensions.values.flatMap { it.sources }
}
