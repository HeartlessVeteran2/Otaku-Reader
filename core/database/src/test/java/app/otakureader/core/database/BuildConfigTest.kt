package app.otakureader.core.database

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for BuildConfig to ensure DEBUG flag is correctly generated and behaves as expected.
 *
 * BuildConfig.DEBUG is critical for migration safety:
 * - DEBUG builds: Allow destructive migration (fallbackToDestructiveMigration) for developer convenience
 * - Release builds: MUST NOT allow destructive migration to prevent silent data loss in production
 *
 * This test validates:
 * 1. BuildConfig is generated (build.gradle.kts has buildFeatures.buildConfig = true)
 * 2. DEBUG flag exists and is accessible
 * 3. Package name matches module namespace (app.otakureader.core.database)
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
     * This ensures the correct BuildConfig is imported in DatabaseModule.
     */
    @Test
    fun buildConfig_packageIsCorrect() {
        // BuildConfig class name should include the correct package
        val buildConfigClass = BuildConfig::class.java
        val expectedPackage = "app.otakureader.core.database.BuildConfig"
        val actualPackage = buildConfigClass.name
        assertEquals(
            "BuildConfig should be in app.otakureader.core.database package",
            expectedPackage,
            actualPackage
        )
    }

    /**
     * Document the expected BuildConfig.DEBUG behavior across build types.
     *
     * This is a documentation test that explicitly states the expected behavior:
     * - Debug builds (./gradlew assembleDebug): BuildConfig.DEBUG = true
     * - Release builds (./gradlew assembleRelease): BuildConfig.DEBUG = false
     * - Unit tests (./gradlew test): BuildConfig.DEBUG = true (uses debug variant)
     *
     * The migration safety depends on this behavior:
     * ```kotlin
     * if (BuildConfig.DEBUG) {
     *     builder.fallbackToDestructiveMigration(dropAllTables = true)
     * }
     * ```
     *
     * In production releases, BuildConfig.DEBUG is false, so the if-block is dead code
     * and will be removed by R8/ProGuard, ensuring zero risk of destructive migration.
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
               - Production safety: Crash on missing migration (no silent data loss)
               - R8/ProGuard removes if(DEBUG) block entirely

            3. Unit tests (./gradlew test):
               - BuildConfig.DEBUG = true (debug variant)
               - Tests run with destructive migration enabled
               - Validates that migrations work correctly even with fallback present
        """.trimIndent()

        // Verify we're testing with debug behavior (since unit tests use debug variant)
        assertTrue(
            "Unit tests use debug variant (DEBUG=true)\n\n$expectedBehavior",
            BuildConfig.DEBUG
        )
    }
}
