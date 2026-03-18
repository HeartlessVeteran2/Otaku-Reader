package app.otakureader.core.extension.loader

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for ExtensionLoadingUtils focusing on NPE prevention and error handling.
 */
class ExtensionLoadingUtilsTest {

    @Test
    fun `createClassLoader throws IllegalArgumentException for blank apkPath`() {
        try {
            ExtensionLoadingUtils.createClassLoader(
                apkPath = "",
                nativeLibDir = null
            )
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `instantiateClass throws IllegalArgumentException for blank className`() {
        val mockClassLoader = mockk<ClassLoader>(relaxed = true)

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

    // Note: Testing instantiateClass with a real ChildFirstPathClassLoader requires Android runtime
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
