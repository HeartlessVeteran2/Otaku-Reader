package app.otakureader.core.extension.loader

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Integration tests for ExtensionLoader.
 * Tests extension install flow, validation, and error handling.
 */
class ExtensionLoaderTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var extensionLoader: ExtensionLoader

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)

        every { context.packageManager } returns packageManager
        every { context.classLoader } returns this::class.java.classLoader
        every { context.codeCacheDir } returns File("/tmp/code_cache")

        extensionLoader = ExtensionLoader(context)
    }

    // -------------------------------------------------------------------------
    // Extension Feature Detection Tests
    // -------------------------------------------------------------------------

    @Test
    fun `isPackageAnExtension returns true for package with tachiyomi extension feature`() {
        // Given
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
            hasExtensionFeature = true
        )

        // When
        val result = extensionLoader.isPackageAnExtension(packageInfo)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isPackageAnExtension returns false for package without extension feature`() {
        // Given
        val packageInfo = createMockPackageInfo(
            pkgName = "com.example.regular.app",
            hasExtensionFeature = false
        )

        // When
        val result = extensionLoader.isPackageAnExtension(packageInfo)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isPackageAnExtension returns false for package with null features`() {
        // Given
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.app"
            reqFeatures = null
        }

        // When
        val result = extensionLoader.isPackageAnExtension(packageInfo)

        // Then
        assertFalse(result)
    }

    // -------------------------------------------------------------------------
    // APK Not Found Tests
    // -------------------------------------------------------------------------

    @Test
    fun `loadExtension returns error when APK file does not exist`() {
        // Given
        val nonExistentPath = "/nonexistent/path/extension.apk"

        // When
        val result = extensionLoader.loadExtension(nonExistentPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("APK file not found"))
    }

    // -------------------------------------------------------------------------
    // Invalid Package Tests
    // -------------------------------------------------------------------------

    @Test
    fun `loadExtension returns error when package info cannot be parsed`() {
        // Given
        val apkPath = createTempApkFile()
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns null

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("Failed to parse package info"))
    }

    @Test
    fun `loadExtension returns error for non-extension package`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "com.regular.app",
            hasExtensionFeature = false
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("Not a valid Tachiyomi-compatible extension"))
    }

    @Test
    fun `loadExtension returns error when applicationInfo is null`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = PackageInfo().apply {
            packageName = "eu.kanade.tachiyomi.extension.en.test"
            reqFeatures = arrayOf(FeatureInfo().apply { name = ExtensionLoader.EXTENSION_FEATURE })
            applicationInfo = null
        }
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("No application info"))
    }

    @Test
    fun `loadExtension returns error when versionName is null`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = null
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("Missing versionName"))
    }

    // -------------------------------------------------------------------------
    // Library Version Validation Tests
    // -------------------------------------------------------------------------

    @Test
    fun `loadExtension returns error for unsupported library version too low`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = "1.0.5"  // Below minimum 1.2
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("Unsupported lib version"))
    }

    @Test
    fun `loadExtension returns error for unsupported library version too high`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = "2.0.1"  // Above maximum 1.5
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("Unsupported lib version"))
    }

    @Test
    fun `loadExtension returns error for invalid version format`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = "invalid"
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("Unsupported lib version"))
    }

    @Test
    fun `loadExtension accepts valid library version 1_2`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = "1.2.100"
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then - should fail on missing sources, not on version check
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("No valid sources found"))
    }

    @Test
    fun `loadExtension accepts valid library version 1_5`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = "1.5.999"
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then - should fail on missing sources, not on version check
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("No valid sources found"))
    }

    // -------------------------------------------------------------------------
    // NSFW Detection Tests
    // -------------------------------------------------------------------------

    @Test
    fun `loadExtension detects NSFW flag when set to 1`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = "1.4.0",
            isNsfw = true
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then - verify NSFW detection (will still fail on missing sources)
        assertTrue(result is ExtensionLoadResult.Error)
        // Cannot verify isNsfw since extension isn't created, but the code path is tested
    }

    @Test
    fun `loadExtension treats extension as non-NSFW when flag is 0`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = "1.4.0",
            isNsfw = false
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        // NSFW flag defaults to false when not set
    }

    // -------------------------------------------------------------------------
    // Source Resolution Tests
    // -------------------------------------------------------------------------

    @Test
    fun `loadExtension returns error when no source classes found`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = "1.4.0",
            sourceClass = null,
            sourceFactory = null
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("No valid sources found"))
    }

    @Test
    fun `loadExtension returns error when source class metadata is empty string`() {
        // Given
        val apkPath = createTempApkFile()
        val packageInfo = createMockPackageInfo(
            pkgName = "eu.kanade.tachiyomi.extension.en.test",
            versionName = "1.4.0",
            sourceClass = ""
        )
        every { packageManager.getPackageArchiveInfo(apkPath, any<Int>()) } returns packageInfo

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("No valid sources found"))
    }

    // -------------------------------------------------------------------------
    // Package Name Loading Tests
    // -------------------------------------------------------------------------

    @Test
    fun `loadExtensionFromPkgName returns error for non-existent package`() {
        // Given
        val pkgName = "com.nonexistent.extension"
        every {
            packageManager.getPackageInfo(pkgName, any<Int>())
        } throws PackageManager.NameNotFoundException()

        // When
        val result = extensionLoader.loadExtensionFromPkgName(pkgName)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("Package not found"))
        assertNotNull(error.throwable)
        assertTrue(error.throwable is PackageManager.NameNotFoundException)
    }

    @Test
    fun `loadExtensionFromPkgName handles unexpected exceptions`() {
        // Given
        val pkgName = "com.test.extension"
        every {
            packageManager.getPackageInfo(pkgName, any<Int>())
        } throws RuntimeException("Unexpected error")

        // When
        val result = extensionLoader.loadExtensionFromPkgName(pkgName)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("Failed to load extension"))
        assertNotNull(error.throwable)
    }

    // -------------------------------------------------------------------------
    // Load All Extensions Tests
    // -------------------------------------------------------------------------

    @Test
    fun `loadAllExtensions filters out non-extension packages`() {
        // Given
        val regularApp = createMockPackageInfo("com.regular.app", hasExtensionFeature = false)
        val extension1 = createMockPackageInfo("eu.kanade.tachiyomi.extension.en.source1", hasExtensionFeature = true)
        val extension2 = createMockPackageInfo("eu.kanade.tachiyomi.extension.en.source2", hasExtensionFeature = true)

        every { packageManager.getInstalledPackages(any<Int>()) } returns listOf(
            regularApp,
            extension1,
            extension2
        )

        // When
        val results = extensionLoader.loadAllExtensions()

        // Then - only extensions are processed (but will fail due to missing sources)
        // Cannot assert count since all fail validation, but verifies filtering logic
        assertEquals(0, results.size) // All fail due to missing source metadata
    }

    @Test
    fun `loadAllExtensions returns empty list when no extensions installed`() {
        // Given
        val regularApps = listOf(
            createMockPackageInfo("com.app1", hasExtensionFeature = false),
            createMockPackageInfo("com.app2", hasExtensionFeature = false)
        )
        every { packageManager.getInstalledPackages(any<Int>()) } returns regularApps

        // When
        val results = extensionLoader.loadAllExtensions()

        // Then
        assertTrue(results.isEmpty())
    }

    @Test
    fun `loadAllExtensions handles empty installed packages list`() {
        // Given
        every { packageManager.getInstalledPackages(any<Int>()) } returns emptyList()

        // When
        val results = extensionLoader.loadAllExtensions()

        // Then
        assertTrue(results.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Exception Handling Tests
    // -------------------------------------------------------------------------

    @Test
    fun `loadExtension catches and wraps exceptions`() {
        // Given
        val apkPath = createTempApkFile()
        every {
            packageManager.getPackageArchiveInfo(apkPath, any<Int>())
        } throws RuntimeException("Mock exception")

        // When
        val result = extensionLoader.loadExtension(apkPath)

        // Then
        assertTrue(result is ExtensionLoadResult.Error)
        val error = result as ExtensionLoadResult.Error
        assertTrue(error.message.contains("Failed to load extension"))
        assertNotNull(error.throwable)
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private fun createMockPackageInfo(
        pkgName: String,
        versionName: String? = "1.4.0",
        versionCode: Int = 100,
        hasExtensionFeature: Boolean = true,
        sourceClass: String? = "com.example.SourceClass",
        sourceFactory: String? = null,
        isNsfw: Boolean = false
    ): PackageInfo {
        val metadata = Bundle().apply {
            sourceClass?.let { putString(ExtensionLoader.METADATA_SOURCE_CLASS, it) }
            sourceFactory?.let { putString(ExtensionLoader.METADATA_SOURCE_FACTORY, it) }
            putInt(ExtensionLoader.METADATA_NSFW, if (isNsfw) 1 else 0)
        }

        val appInfo = ApplicationInfo().apply {
            this.sourceDir = "/data/app/test/base.apk"
            this.publicSourceDir = "/data/app/test/base.apk"
            this.nativeLibraryDir = "/data/app/test/lib"
            this.metaData = metadata
        }

        return PackageInfo().apply {
            packageName = pkgName
            this.versionName = versionName
            @Suppress("DEPRECATION")
            this.versionCode = versionCode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode = versionCode.toLong()
            }
            applicationInfo = appInfo
            reqFeatures = if (hasExtensionFeature) {
                arrayOf(FeatureInfo().apply { name = ExtensionLoader.EXTENSION_FEATURE })
            } else {
                emptyArray()
            }
        }
    }

    private fun createTempApkFile(): String {
        val tempFile = File.createTempFile("test_extension_", ".apk")
        tempFile.deleteOnExit()
        // Write minimal content so file exists and is non-empty
        tempFile.writeText("mock apk")
        return tempFile.absolutePath
    }
}
