package app.otakureader.core.tachiyomi.compat

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import java.io.File

/**
 * Loads Tachiyomi extension APKs and instantiates their Source classes.
 *
 * This loader uses DexClassLoader to load classes from Tachiyomi extension APKs,
 * discovers Source implementations, and wraps them with TachiyomiSourceAdapter.
 */
class TachiyomiExtensionLoader(
    private val packageManager: PackageManager,
    private val cacheDir: File
) {

    companion object {
        const val TACHIYOMI_EXTENSION_FEATURE = "tachiyomi.extension"
        const val TACHIYOMI_EXTENSION_METADATA = "tachiyomi.extension"
        const val TACHIYOMI_SOURCE_CLASS = "eu.kanade.tachiyomi.extension"

        // Source class patterns to search for
        private val SOURCE_CLASS_PATTERNS = listOf(
            "eu.kanade.tachiyomi.extension.",
            "eu.kanade.tachiyomi.source.online."
        )
    }

    private val loadedExtensions = mutableMapOf<String, LoadedExtension>()
    private val manifestParser = TachiyomiManifestParser()

    /**
     * Data class representing a loaded extension
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
        val classLoader: DexClassLoader
    )

    /**
     * Load all installed Tachiyomi extensions
     */
    fun loadAllExtensions(): List<LoadedExtension> {
        val extensions = mutableListOf<LoadedExtension>()

        // Find all apps with the Tachiyomi extension feature
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_META_DATA or
                    PackageManager.GET_CONFIGURATIONS.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(
                PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS
            )
        }

        for (packageInfo in installedPackages) {
            val extension = loadExtension(packageInfo)
            if (extension != null) {
                extensions.add(extension)
            }
        }

        return extensions
    }

    /**
     * Load a specific extension by package name
     */
    fun loadExtension(packageName: String): LoadedExtension? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            }
            loadExtension(packageInfo)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load extension from an APK file path
     */
    fun loadExtensionFromApk(apkPath: String): LoadedExtension? {
        return try {
            val packageInfo = packageManager.getPackageArchiveInfo(
                apkPath,
                PackageManager.GET_META_DATA
            ) ?: return null

            // Set the source directory for the application info
            packageInfo.applicationInfo?.sourceDir = apkPath
            packageInfo.applicationInfo?.publicSourceDir = apkPath

            loadExtension(packageInfo)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load extension from PackageInfo
     */
    private fun loadExtension(packageInfo: PackageInfo): LoadedExtension? {
        val packageName = packageInfo.packageName

        // Check if already loaded
        loadedExtensions[packageName]?.let { return it }

        // Check if this is a Tachiyomi extension
        val appInfo = packageInfo.applicationInfo ?: return null

        // Verify it's a Tachiyomi extension by checking metadata
        val metadata = appInfo.metaData
        if (metadata?.getString(TACHIYOMI_EXTENSION_METADATA) == null) {
            // Check for feature flag as fallback
            val hasFeature = packageInfo.reqFeatures?.any {
                it.name == TACHIYOMI_EXTENSION_FEATURE
            } ?: false
            if (!hasFeature) return null
        }

        // Parse manifest for extension info
        val manifestInfo = try {
            parseExtensionManifest(appInfo)
        } catch (e: Exception) {
            createManifestInfoFromPackage(packageInfo, appInfo)
        }

        // Create DexClassLoader for the APK
        val apkPath = appInfo.sourceDir
        val nativeLibDir = appInfo.nativeLibraryDir
        val optimizedDir = File(cacheDir, "tachiyomi-dex").apply { mkdirs() }

        val classLoader = DexClassLoader(
            apkPath,
            optimizedDir.absolutePath,
            nativeLibDir,
            TachiyomiExtensionLoader::class.java.classLoader
        )

        // Find and instantiate Source classes
        val sources = instantiateSources(classLoader, manifestInfo)

        if (sources.isEmpty()) {
            classLoader.close()
            return null
        }

        val extension = LoadedExtension(
            packageName = packageName,
            name = manifestInfo.name ?: packageInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: packageName,
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            lang = manifestInfo.lang ?: "all",
            isNsfw = manifestInfo.isNsfw,
            sources = sources.map { TachiyomiSourceAdapter(it) },
            apkPath = apkPath,
            classLoader = classLoader
        )

        loadedExtensions[packageName] = extension
        return extension
    }

    /**
     * Parse extension manifest from APK
     */
    private fun parseExtensionManifest(appInfo: ApplicationInfo): TachiyomiManifestParser.ExtensionInfo {
        val apkFile = File(appInfo.sourceDir)
        return manifestParser.parse(apkFile)
    }

    /**
     * Create basic manifest info from package info (fallback)
     */
    private fun createManifestInfoFromPackage(
        packageInfo: PackageInfo,
        appInfo: ApplicationInfo
    ): TachiyomiManifestParser.ExtensionInfo {
        val metadata = appInfo.metaData
        return TachiyomiManifestParser.ExtensionInfo(
            name = appInfo.loadLabel(packageManager)?.toString(),
            lang = metadata?.getString("tachiyomi.extension.lang"),
            isNsfw = metadata?.getBoolean("tachiyomi.extension.nsfw") ?: false,
            sources = emptyList()
        )
    }

    /**
     * Find and instantiate Source classes from the extension
     */
    private fun instantiateSources(
        classLoader: DexClassLoader,
        manifestInfo: TachiyomiManifestParser.ExtensionInfo
    ): List<CatalogueSource> {
        val sources = mutableListOf<CatalogueSource>()

        // Try to get source class names from manifest first
        val sourceClassNames = manifestInfo.sources.ifEmpty {
            discoverSourceClasses(classLoader)
        }.map { it.className }

        for (className in sourceClassNames) {
            try {
                val source = instantiateSource(classLoader, className)
                if (source != null) {
                    sources.add(source)
                }
            } catch (e: Exception) {
                // Log error but continue trying other sources
                e.printStackTrace()
            }
        }

        return sources
    }

    /**
     * Discover Source classes in the extension by scanning for known patterns
     */
    private fun discoverSourceClasses(classLoader: DexClassLoader): List<TachiyomiManifestParser.SourceInfo> {
        val classes = mutableListOf<TachiyomiManifestParser.SourceInfo>()

        // Try common patterns
        val commonSourceClasses = listOf(
            "Source",
            "MainSource",
            "MangaSource",
            "ExtensionSource"
        )

        // Attempt to load from known package patterns
        for (pattern in SOURCE_CLASS_PATTERNS) {
            for (suffix in commonSourceClasses) {
                try {
                    val className = "$pattern$suffix"
                    classLoader.loadClass(className)
                    classes.add(TachiyomiManifestParser.SourceInfo(
                        name = suffix,
                        className = className
                    ))
                } catch (e: ClassNotFoundException) {
                    // Class doesn't exist, continue
                }
            }
        }

        return classes
    }

    /**
     * Instantiate a single Source class
     */
    @Suppress("UNCHECKED_CAST")
    private fun instantiateSource(classLoader: DexClassLoader, className: String): CatalogueSource? {
        return try {
            val clazz = classLoader.loadClass(className)

            // Check if it's a Source implementation
            if (!Source::class.java.isAssignableFrom(clazz)) {
                return null
            }

            // Try to instantiate - sources typically have a no-arg constructor
            val instance = try {
                clazz.getDeclaredConstructor().newInstance()
            } catch (e: NoSuchMethodException) {
                // Try with context parameter
                null
            }

            when (instance) {
                is CatalogueSource -> instance
                is Source -> {
                    // Wrap non-catalogue sources if possible
                    null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reload an extension
     */
    fun reloadExtension(packageName: String): LoadedExtension? {
        unloadExtension(packageName)
        return loadExtension(packageName)
    }

    /**
     * Unload an extension and release resources
     */
    fun unloadExtension(packageName: String) {
        loadedExtensions.remove(packageName)?.let { extension ->
            try {
                extension.classLoader.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Unload all extensions
     */
    fun unloadAllExtensions() {
        loadedExtensions.keys.toList().forEach { unloadExtension(it) }
    }

    /**
     * Get list of loaded extensions
     */
    fun getLoadedExtensions(): List<LoadedExtension> {
        return loadedExtensions.values.toList()
    }

    /**
     * Check if an extension is loaded
     */
    fun isExtensionLoaded(packageName: String): Boolean {
        return loadedExtensions.containsKey(packageName)
    }

    /**
     * Get all loaded sources from all extensions
     */
    fun getAllSources(): List<TachiyomiSourceAdapter> {
        return loadedExtensions.values.flatMap { it.sources }
    }
}
