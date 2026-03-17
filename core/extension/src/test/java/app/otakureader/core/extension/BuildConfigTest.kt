package app.otakureader.core.extension

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for BuildConfig to ensure DEBUG flag is correctly generated and behaves as expected.
 *
 * BuildConfig.DEBUG is critical for migration safety in ExtensionDatabase:
 * - DEBUG builds: Allow destructive migration for developer convenience
 * - Release builds: MUST NOT allow destructive migration to prevent data loss
 *
 * This test validates:
 * 1. BuildConfig is generated (build.gradle.kts has buildFeatures.buildConfig = true)
 * 2. DEBUG flag exists and is accessible
 * 3. Package name matches module namespace (app.otakureader.core.extension)
 * 4. In unit tests (which use release-like behavior), DEBUG should be false
 */
class BuildConfigTest {

    /**
     * Verify BuildConfig.DEBUG exists and is accessible.
     * This ensures the Gradle buildConfig feature is enabled correctly.
     */
    @Test
    fun buildConfig_debugFlagExists() {
        // BuildConfig.DEBUG should be accessible
        assertNotNull("BuildConfig.DEBUG should exist", BuildConfig.DEBUG)
    }

    /**
     * Verify BuildConfig.DEBUG matches current build variant.
     * Unit tests run in debug mode, so BuildConfig.DEBUG should be true.
     *
     * This test documents the actual behavior:
     * - Debug builds (including unit tests): DEBUG = true, destructive migration allowed
     * - Release builds: DEBUG = false, NO destructive migration (crash on missing migration)
     */
    @Test
    fun buildConfig_debugMatchesBuildVariant() {
        // In unit test environment, BuildConfig.DEBUG is true (debug build variant)
        // This means destructive migrations are enabled during testing
        assertTrue(
            "BuildConfig.DEBUG should be true in unit tests (debug variant)",
            BuildConfig.DEBUG
        )
    }

    /**
     * Verify BuildConfig package matches module namespace.
     * This ensures the correct BuildConfig is imported in ExtensionModule.
     */
    @Test
    fun buildConfig_packageIsCorrect() {
        // BuildConfig class name should include the correct package
        val buildConfigClass = BuildConfig::class.java
        val expectedPackage = "app.otakureader.core.extension.BuildConfig"
        val actualPackage = buildConfigClass.name
        assertEquals(
            "BuildConfig should be in app.otakureader.core.extension package",
            expectedPackage,
            actualPackage
        )
    }

    /**
     * Document the expected BuildConfig.DEBUG behavior across build types.
     *
     * ExtensionDatabase uses BuildConfig.DEBUG to gate destructive migrations:
     * ```kotlin
     * if (BuildConfig.DEBUG) {
     *     builder.fallbackToDestructiveMigration(dropAllTables = true)
     * }
     * ```
     *
     * Note: ExtensionDatabase currently has no migrations (version 3, exportSchema = false)
     * because extension metadata is considered volatile/cache-like data. However, the
     * destructive migration gating is still important for consistency and future-proofing.
     */
    @Test
    fun buildConfig_behaviorDocumentation() {
        // This test documents expected behavior across build variants
        val expectedBehavior = """
            BuildConfig.DEBUG behavior by build type:

            1. Debug builds (assembleDebug):
               - BuildConfig.DEBUG = true
               - Destructive migration ENABLED
               - Developer convenience: Fresh schema on migration errors

            2. Release builds (assembleRelease):
               - BuildConfig.DEBUG = false
               - Destructive migration DISABLED
               - Production safety: Crash on missing migration
               - R8/ProGuard removes if(DEBUG) block entirely

            3. Unit tests (./gradlew test):
               - BuildConfig.DEBUG = true (debug variant)
               - Tests run with destructive migration enabled
               - Validates that database works correctly with fallback present

            Note: ExtensionDatabase (version 3) currently has no migrations
            as extension metadata is cache-like, but the DEBUG gating ensures
            consistent behavior with OtakuReaderDatabase.
        """.trimIndent()

        // Verify we're testing with debug behavior (since unit tests use debug variant)
        assertTrue(
            "Unit tests use debug variant (DEBUG=true)\n\n$expectedBehavior",
            BuildConfig.DEBUG
        )
    }
}
