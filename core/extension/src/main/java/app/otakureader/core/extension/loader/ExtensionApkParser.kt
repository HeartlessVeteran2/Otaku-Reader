package app.otakureader.core.extension.loader

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.otakureader.core.extension.loader.ExtensionLoadingUtils.fixBasePaths
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads package metadata from extension APKs (installed or on-disk).
 *
 * Concentrates everything related to invoking [PackageManager] and normalising the
 * resulting [PackageInfo] (Android 13+ base-path fixups, version-code compatibility
 * across SDK levels, NSFW flag, feature detection).
 *
 * Extracted from `ExtensionLoader` so each parsing concern is independently
 * unit-testable. Does not perform signature verification or class loading — see
 * [ExtensionSignatureVerifier] and [ExtensionClassLoaderFactory].
 */
@Singleton
class ExtensionApkParser @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Parse an extension APK from a file path. Fixes base paths so assets and icon
     * loading work on Android 13+. Returns `null` if the APK cannot be parsed.
     */
    fun parseApk(apkPath: String): PackageInfo? {
        val packageInfo = packageManager.getPackageArchiveInfo(apkPath, PACKAGE_FLAGS) ?: return null
        packageInfo.applicationInfo?.fixBasePaths(apkPath)
        return packageInfo
    }

    /**
     * Look up an installed package by name. Returns `null` if not installed.
     */
    fun getInstalledPackage(pkgName: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    pkgName,
                    PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Enumerate every package currently installed on the device with the flags this
     * loader needs (signatures, metadata, etc.).
     */
    fun getInstalledPackages(): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(PACKAGE_FLAGS)
        }
    }

    /** True if the package declares the Tachiyomi extension uses-feature flag. */
    fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean =
        ExtensionLoadingUtils.isPackageAnExtension(pkgInfo)

    /** Read the NSFW metadata flag (`tachiyomi.extension.nsfw == 1`). */
    fun isNsfw(appInfo: ApplicationInfo): Boolean = ExtensionLoadingUtils.isNsfw(appInfo)

    /**
     * Return the version code as a [Long], handling the long-version-code transition
     * introduced in Android P.
     */
    fun getVersionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    /**
     * Return the truncated 32-bit version code preserved in the [Extension] domain
     * model. Equivalent to `versionCode.toInt()` on older SDKs.
     */
    fun getVersionCodeInt(packageInfo: PackageInfo): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    /**
     * Load a label for the package via the package manager and strip the standard
     * "Tachiyomi: " prefix so display names look natural. If the label has no such
     * prefix it is returned unchanged (matches Tachiyomi/Komikku behaviour).
     *
     * Returns `null` only when [ApplicationInfo.loadLabel] itself returns `null`.
     */
    fun loadLabel(appInfo: ApplicationInfo): String? {
        return appInfo.loadLabel(packageManager)?.toString()?.substringAfter("Tachiyomi: ")
    }

    /** Resolve the directory used to store private extensions (`filesDir/exts/`). */
    fun getPrivateExtensionDir(): File = File(context.filesDir, "exts")

    companion object {
        @Suppress("DEPRECATION")
        val PACKAGE_FLAGS: Int = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
    }
}
