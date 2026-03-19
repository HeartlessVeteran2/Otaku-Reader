package app.otakureader.core.extension.loader

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

/**
 * Shared utilities for loading Tachiyomi-compatible extension APKs.
 *
 * These utilities are used by ExtensionLoader to avoid code duplication.
 * TachiyomiExtensionLoader has its own copy to avoid circular dependencies.
 */
object ExtensionLoadingUtils {

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
     * @param classLoader The DexClassLoader to use for loading the class
     * @param className The fully-qualified class name to instantiate
     * @return Instance of the class, or null if instantiation fails
     */
    fun instantiateClass(classLoader: ClassLoader, className: String): Any? {
        require(className.isNotBlank()) { "Class name must not be blank" }

        return try {
            Class.forName(className, false, classLoader)
                .getDeclaredConstructor()
                .newInstance()
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
     * Resolve Source instances from extension metadata.
     *
     * Checks METADATA_SOURCE_FACTORY first; if absent, reads class names from
     * METADATA_SOURCE_CLASS. Both keys support multiple class names separated by `;`.
     * Relative class names starting with `.` are expanded using the package name.
     *
     * @param metadata Bundle containing extension metadata
     * @param pkgName Package name for resolving relative class names
     * @param classLoader DexClassLoader for loading classes
     * @param filterType Optional class to filter by (e.g., CatalogueSource::class.java)
     * @return List of Source instances
     */
    fun resolveSourcesFromMetadata(
        metadata: android.os.Bundle,
        pkgName: String,
        classLoader: ClassLoader,
        filterType: Class<*>? = null
    ): List<Source> {
        // Prefer SourceFactory when declared
        val factoryClassName = metadata.getString(METADATA_SOURCE_FACTORY)
        if (!factoryClassName.isNullOrBlank()) {
            val resolvedClass = resolveClassName(factoryClassName.trim(), pkgName)
            val instance = instantiateClass(classLoader, resolvedClass)
            if (instance is SourceFactory) {
                val sources = instance.createSources()
                return if (filterType != null) {
                    sources.filter { filterType.isInstance(it) }
                } else {
                    sources
                }
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
                    is SourceFactory -> {
                        val sources = instance.createSources()
                        if (filterType != null) {
                            sources.filter { filterType.isInstance(it) }
                        } else {
                            sources
                        }
                    }
                    is Source -> {
                        if (filterType == null || filterType.isInstance(instance)) {
                            listOf(instance)
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }
    }

    /**
     * Resolve Source instances from ApplicationInfo metadata.
     *
     * This is a convenience overload that extracts metadata from ApplicationInfo.
     *
     * @param appInfo ApplicationInfo containing extension metadata
     * @param pkgName Package name for resolving relative class names
     * @param classLoader DexClassLoader for loading classes
     * @param filterType Optional class to filter by (e.g., CatalogueSource::class.java)
     * @return List of Source instances, empty if metadata is null
     */
    fun resolveSourcesFromMetadata(
        appInfo: ApplicationInfo,
        pkgName: String,
        classLoader: ClassLoader,
        filterType: Class<*>? = null
    ): List<Source> {
        val metadata = appInfo.metaData ?: return emptyList()
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
