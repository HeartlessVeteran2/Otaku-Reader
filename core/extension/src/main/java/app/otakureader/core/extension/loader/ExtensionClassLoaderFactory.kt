package app.otakureader.core.extension.loader

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds [ChildFirstPathClassLoader]s for extension APKs.
 *
 * Extracted from `ExtensionLoader` so the class-loader wiring is independently
 * testable. Implementation delegates to [ExtensionLoadingUtils.createClassLoader],
 * which is also used by `TachiyomiExtensionLoader` to keep both loaders behaving
 * identically (matching Tachiyomi/Komikku's child-first strategy).
 */
@Singleton
class ExtensionClassLoaderFactory @Inject constructor() {

    /**
     * Create a [ChildFirstPathClassLoader] for the given APK.
     *
     * @param apkPath Path to the APK file (must be non-blank).
     * @param nativeLibDir Directory containing native libraries, or `null`.
     * @param parentClassLoader Parent class loader; the extension's classes are
     *  preferred over the parent (child-first), preventing class-version conflicts
     *  with the host app.
     * @throws IllegalArgumentException if [apkPath] is blank.
     */
    fun create(
        apkPath: String,
        nativeLibDir: String?,
        parentClassLoader: ClassLoader,
    ): ChildFirstPathClassLoader {
        return ExtensionLoadingUtils.createClassLoader(
            apkPath = apkPath,
            nativeLibDir = nativeLibDir,
            parentClassLoader = parentClassLoader,
        )
    }
}
