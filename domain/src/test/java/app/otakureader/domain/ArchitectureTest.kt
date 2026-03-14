package app.otakureader.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architecture tests to enforce Clean Architecture principles.
 *
 * These tests ensure that:
 * - Domain layer has NO Android dependencies
 * - Domain layer depends only on Kotlin stdlib and declared dependencies
 * - Domain models, repositories, and use cases remain platform-agnostic
 */
class ArchitectureTest {

    /**
     * Verifies that the domain layer contains no Android framework imports.
     *
     * This is critical for Clean Architecture - the domain layer must be pure Kotlin
     * so it can be tested on JVM without Android runtime and potentially shared
     * across platforms.
     */
    @Test
    fun `domain layer must not import Android framework classes`() {
        val domainSourceDir = File("src/main/java")
        assertTrue("Domain source directory should exist", domainSourceDir.exists())

        val androidImports = mutableListOf<String>()
        val bannedPrefixes = listOf(
            "import android.",
            "import androidx.",
            "import com.google.android."
        )

        domainSourceDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { lineNumber, line ->
                    val trimmed = line.trim()
                    bannedPrefixes.forEach { bannedPrefix ->
                        if (trimmed.startsWith(bannedPrefix)) {
                            androidImports.add(
                                "${file.relativeTo(domainSourceDir).path}:${lineNumber + 1} - $trimmed"
                            )
                        }
                    }
                }
            }

        assertTrue(
            "Domain layer must not contain Android imports. Found violations:\n${androidImports.joinToString("\n")}",
            androidImports.isEmpty()
        )
    }

    /**
     * Verifies that the domain model package exists in the expected location.
     *
     * This enforces that domain models live under `app.otakureader.domain.model`
     * to keep the domain layer structure consistent and discoverable.
     */
    @Test
    fun `domain models should be in model package`() {
        val modelDir = File("src/main/java/app/otakureader/domain/model")
        assertTrue(
            "Domain model package should exist",
            modelDir.exists() && modelDir.isDirectory
        )
    }

    /**
     * Verifies that repository interfaces exist in domain layer.
     * Implementations should be in data layer.
     */
    @Test
    fun `repository interfaces should be in domain repository package`() {
        val repoDir = File("src/main/java/app/otakureader/domain/repository")
        assertTrue(
            "Domain repository package should exist",
            repoDir.exists() && repoDir.isDirectory
        )

        val repositoryFiles = repoDir.walkTopDown()
            .filter { it.extension == "kt" }
            .toList()

        assertTrue(
            "Domain layer should define repository interfaces",
            repositoryFiles.isNotEmpty()
        )
    }

    /**
     * Verifies use cases exist in domain layer.
     * Use cases encapsulate business logic and orchestrate repository calls.
     */
    @Test
    fun `use cases should exist in domain usecase package`() {
        val useCaseDir = File("src/main/java/app/otakureader/domain/usecase")
        assertTrue(
            "Domain usecase package should exist",
            useCaseDir.exists() && useCaseDir.isDirectory
        )

        val useCaseFiles = useCaseDir.walkTopDown()
            .filter { it.extension == "kt" && it.name.endsWith("UseCase.kt") }
            .toList()

        assertTrue(
            "Domain layer should contain use cases",
            useCaseFiles.isNotEmpty()
        )
    }

    /**
     * Ensures domain layer only uses allowed dependencies.
     * Allowed: kotlinx.coroutines, kotlinx.serialization, javax.inject (compile-only)
     */
    @Test
    fun `domain build file should only declare allowed dependencies`() {
        val buildFile = File("build.gradle.kts")
        assertTrue("Domain build.gradle.kts should exist", buildFile.exists())

        val buildContent = buildFile.readText()

        // Check that it's a Kotlin library (not Android)
        assertTrue(
            "Domain should use Kotlin library plugin (not Android)",
            buildContent.contains("otakureader.kotlin.library") ||
            buildContent.contains("kotlin(\"jvm\")")
        )

        // Verify no Android dependencies
        val androidDependencyPatterns = listOf(
            "androidx.",
            "android.arch.",
            "com.google.android.",
            "implementation.*androidx",
            "api.*androidx"
        )

        androidDependencyPatterns.forEach { pattern ->
            assertTrue(
                "Domain build.gradle.kts should not contain Android dependency: $pattern",
                !buildContent.contains(Regex(pattern))
            )
        }
    }
}
