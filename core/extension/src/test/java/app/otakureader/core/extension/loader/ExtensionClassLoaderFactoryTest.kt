package app.otakureader.core.extension.loader

import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [ExtensionClassLoaderFactory]. Verifies that the factory
 * propagates argument validation from [ExtensionLoadingUtils.createClassLoader].
 *
 * Note: instantiating a real [ChildFirstPathClassLoader] requires the Android
 * runtime, so the success path is exercised by the existing instrumented
 * extension-loader integration tests rather than this JVM unit test.
 */
class ExtensionClassLoaderFactoryTest {

    private val factory = ExtensionClassLoaderFactory()

    @Test
    fun `create rejects blank apkPath`() {
        try {
            factory.create(
                apkPath = "",
                nativeLibDir = null,
                parentClassLoader = ClassLoader.getSystemClassLoader(),
            )
            fail("Expected IllegalArgumentException for blank apkPath")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
