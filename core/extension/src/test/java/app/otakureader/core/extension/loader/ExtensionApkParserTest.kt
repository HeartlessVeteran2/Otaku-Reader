package app.otakureader.core.extension.loader

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for [ExtensionApkParser]. Verifies APK parsing, base-path fixup,
 * version-code helpers and feature/NSFW detection in isolation from
 * signature verification and class loading.
 */
class ExtensionApkParserTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var parser: ExtensionApkParser
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        every { context.packageManager } returns packageManager
        filesDir = createTempDirectory(prefix = "apk_parser_test_").toFile()
        every { context.filesDir } returns filesDir
        parser = ExtensionApkParser(context)
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `parseApk returns null when package manager returns null`() {
        every { packageManager.getPackageArchiveInfo(any<String>(), any<Int>()) } returns null

        val result = parser.parseApk("/some/path.apk")

        assertNull(result)
    }

    @Test
    fun `parseApk fixes null base paths on returned ApplicationInfo`() {
        val apkPath = "/data/app/test.apk"
        val appInfo = ApplicationInfo().apply {
            sourceDir = null
            publicSourceDir = null
        }
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.ext"
            applicationInfo = appInfo
        }
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        val result = parser.parseApk(apkPath)

        assertNotNull(result)
        assertEquals(apkPath, result!!.applicationInfo!!.sourceDir)
        assertEquals(apkPath, result.applicationInfo!!.publicSourceDir)
    }

    @Test
    fun `parseApk preserves existing non-null base paths`() {
        val apkPath = "/data/app/test.apk"
        val existing = "/already/set.apk"
        val appInfo = ApplicationInfo().apply {
            sourceDir = existing
            publicSourceDir = existing
        }
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.ext"
            applicationInfo = appInfo
        }
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        val result = parser.parseApk(apkPath)

        assertEquals(existing, result!!.applicationInfo!!.sourceDir)
        assertEquals(existing, result.applicationInfo!!.publicSourceDir)
    }

    @Test
    fun `getInstalledPackage returns null when NameNotFoundException is thrown`() {
        @Suppress("DEPRECATION")
        every { packageManager.getPackageInfo("missing.pkg", any<Int>()) } throws
            PackageManager.NameNotFoundException()
        every { packageManager.getPackageInfo("missing.pkg", any<PackageManager.PackageInfoFlags>()) } throws
            PackageManager.NameNotFoundException()

        val result = parser.getInstalledPackage("missing.pkg")

        assertNull(result)
    }

    @Test
    fun `getInstalledPackage returns PackageInfo when found`() {
        val pi = PackageInfo().apply { packageName = "found.pkg" }
        @Suppress("DEPRECATION")
        every { packageManager.getPackageInfo("found.pkg", any<Int>()) } returns pi
        every { packageManager.getPackageInfo("found.pkg", any<PackageManager.PackageInfoFlags>()) } returns pi

        val result = parser.getInstalledPackage("found.pkg")

        assertSame(pi, result)
    }

    @Test
    fun `getInstalledPackages delegates to package manager`() {
        val pkgs = listOf(PackageInfo().apply { packageName = "a" })
        @Suppress("DEPRECATION")
        every { packageManager.getInstalledPackages(any<Int>()) } returns pkgs
        every { packageManager.getInstalledPackages(any<PackageManager.PackageInfoFlags>()) } returns pkgs

        val result = parser.getInstalledPackages()

        assertEquals(pkgs, result)
    }

    @Test
    fun `isPackageAnExtension true when extension feature declared`() {
        val pi = PackageInfo().apply {
            reqFeatures = arrayOf(FeatureInfo().apply { name = ExtensionLoadingUtils.EXTENSION_FEATURE })
        }

        assertTrue(parser.isPackageAnExtension(pi))
    }

    @Test
    fun `isPackageAnExtension false when feature missing`() {
        val pi = PackageInfo().apply {
            reqFeatures = arrayOf(FeatureInfo().apply { name = "some.other.feature" })
        }

        assertFalse(parser.isPackageAnExtension(pi))
    }

    @Test
    fun `isNsfw reads metadata flag`() {
        val nsfwBundle = mockk<Bundle>()
        every { nsfwBundle.getInt(ExtensionLoadingUtils.METADATA_NSFW) } returns 1
        val safeBundle = mockk<Bundle>()
        every { safeBundle.getInt(ExtensionLoadingUtils.METADATA_NSFW) } returns 0

        val nsfw = ApplicationInfo().apply { metaData = nsfwBundle }
        val safe = ApplicationInfo().apply { metaData = safeBundle }
        val missing = ApplicationInfo().apply { metaData = null }

        assertTrue(parser.isNsfw(nsfw))
        assertFalse(parser.isNsfw(safe))
        assertFalse(parser.isNsfw(missing))
    }

    @Test
    fun `getVersionCode and getVersionCodeInt agree for small values`() {
        val pi = PackageInfo().apply {
            @Suppress("DEPRECATION")
            versionCode = 42
        }

        assertEquals(42L, parser.getVersionCode(pi))
        assertEquals(42, parser.getVersionCodeInt(pi))
    }

    @Test
    fun `loadLabel strips Tachiyomi prefix`() {
        val appInfo = mockk<ApplicationInfo>()
        every { appInfo.loadLabel(packageManager) } returns "Tachiyomi: MangaDex"

        val label = parser.loadLabel(appInfo)

        assertEquals("MangaDex", label)
    }

    @Test
    fun `loadLabel returns label unchanged when no prefix`() {
        val appInfo = mockk<ApplicationInfo>()
        every { appInfo.loadLabel(packageManager) } returns "Other Source"

        assertEquals("Other Source", parser.loadLabel(appInfo))
    }

    @Test
    fun `getPrivateExtensionDir resolves under filesDir`() {
        val dir = parser.getPrivateExtensionDir()

        assertEquals(File(filesDir, "exts"), dir)
    }
}
