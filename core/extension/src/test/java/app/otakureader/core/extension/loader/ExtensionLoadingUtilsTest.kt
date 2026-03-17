package app.otakureader.core.extension.loader

import dalvik.system.DexClassLoader
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Unit tests for ExtensionLoadingUtils focusing on NPE prevention and error handling.
 */
class ExtensionLoadingUtilsTest {

    @Test
    fun `createClassLoader throws IllegalArgumentException for blank apkPath`() {
        try {
            ExtensionLoadingUtils.createClassLoader(
                apkPath = "",
                optimizedDir = File("/tmp/test"),
                nativeLibDir = null
            )
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `createClassLoader throws IllegalStateException when mkdirs fails`() {
        // Create a temporary file, then try to use it as a directory (will fail mkdirs)
        val tempFile = File.createTempFile("test_", ".tmp")
        tempFile.deleteOnExit()

        try {
            ExtensionLoadingUtils.createClassLoader(
                apkPath = "/test/path.apk",
                optimizedDir = File(tempFile, "impossible"),  // Cannot create dir under a file
                nativeLibDir = null
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected - directory creation should fail
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `instantiateClass throws IllegalArgumentException for blank className`() {
        // className validation happens before classLoader is used
        val mockClassLoader = mockk<DexClassLoader>(relaxed = true)

        try {
            ExtensionLoadingUtils.instantiateClass(
                classLoader = mockClassLoader,
                className = ""
            )
            fail("Expected IllegalArgumentException for blank className")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // Note: Testing instantiateClass with a real DexClassLoader requires Android runtime
    // The production code will properly return null for ClassNotFoundException, etc.

    @Test
    fun `resolveClassName expands relative class name`() {
        val result = ExtensionLoadingUtils.resolveClassName(
            className = ".MySource",
            pkgName = "com.example"
        )
        assertEquals("com.example.MySource", result)
    }

    @Test
    fun `resolveClassName keeps absolute class name`() {
        val result = ExtensionLoadingUtils.resolveClassName(
            className = "com.example.MySource",
            pkgName = "com.test"
        )
        assertEquals("com.example.MySource", result)
    }
}
