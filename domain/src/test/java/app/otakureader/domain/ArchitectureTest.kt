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
     * Resolves the domain module root directory.
     * Handles different working directories (CI, IDE, command line).
     */
    private fun resolveDomainModuleRoot(): File {
        // Try to locate from current working directory
        val cwd = File(".").canonicalFile

        // If we're already in the domain module
        if (cwd.name == "domain" && File(cwd, "build.gradle.kts").exists()) {
            return cwd
        }

        // If we're in the project root
        val domainFromRoot = File(cwd, "domain")
        if (domainFromRoot.exists() && File(domainFromRoot, "build.gradle.kts").exists()) {
            return domainFromRoot
        }

        // Try parent directories (for IDE test runs from nested directories)
        var parent = cwd.parentFile
        while (parent != null) {
            val domainFromParent = File(parent, "domain")
            if (domainFromParent.exists() && File(domainFromParent, "build.gradle.kts").exists()) {
                return domainFromParent
            }
            parent = parent.parentFile
        }

        // Fallback to current directory - test will fail with clear message if wrong
        return cwd
    }

    private val domainModuleRoot by lazy { resolveDomainModuleRoot() }

    /**
     * Verifies that the domain layer contains no Android framework imports.
     *
     * This is critical for Clean Architecture - the domain layer must be pure Kotlin
     * so it can be tested on JVM without Android runtime and potentially shared
     * across platforms.
     */
    @Test
    fun `domain layer must not import Android framework classes`() {
        val domainSourceDir = File(domainModuleRoot, "src/main/java")
        assertTrue(
            "Domain source directory should exist at ${domainSourceDir.absolutePath}",
            domainSourceDir.exists()
        )

        val androidImports = mutableListOf<String>()
        val bannedPrefixes = listOf(
            "import android.",
            "import androidx.",
            "import com.google.android."
        )
        val allowedImports = listOf(
            "import androidx.compose.runtime.Immutable" // Allowed: compile-only annotation for immutable data classes
        )

        domainSourceDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { lineNumber, line ->
                    val trimmed = line.trim()
                    bannedPrefixes.forEach { bannedPrefix ->
                        if (trimmed.startsWith(bannedPrefix) && !allowedImports.contains(trimmed)) {
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
        val modelDir = File(domainModuleRoot, "src/main/java/app/otakureader/domain/model")
        assertTrue(
            "Domain model package should exist at ${modelDir.absolutePath}",
            modelDir.exists() && modelDir.isDirectory
        )

        val domainDir = File(domainModuleRoot, "src/main/java/app/otakureader/domain")
        assertTrue(
            "Domain source directory should exist at ${domainDir.absolutePath}",
            domainDir.exists() && domainDir.isDirectory
        )

        val nonModelDataClasses = mutableListOf<String>()

        domainDir.walkTopDown()
            .filter { it.extension == "kt" && !it.toPath().startsWith(modelDir.toPath()) }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    // Match top-level data class declarations only (not indented/nested).
                    // Nested data classes (inside use cases, sealed classes, interfaces)
                    // are tightly coupled to their parent and don't need to live in the model package.
                    val trimmed = line.trimStart()
                    val isIndented = line != trimmed && line.isNotBlank()
                    if (!isIndented &&
                        Regex("""\bdata\s+class\s+\w+""").containsMatchIn(trimmed) &&
                        !trimmed.startsWith("//") &&
                        !trimmed.startsWith("/*") &&
                        !trimmed.startsWith("*")) {
                        nonModelDataClasses += "${file.path}:${index + 1}: ${line.trim()}"
                    }
                }
            }

        assertTrue(
            buildString {
                appendLine("Domain data classes should be located in the model package:")
                appendLine("src/main/java/app/otakureader/domain/model")
                if (nonModelDataClasses.isNotEmpty()) {
                    appendLine("Found data classes outside model package:")
                    nonModelDataClasses.forEach { appendLine(it) }
                }
            },
            nonModelDataClasses.isEmpty()
        )
    }

    /**
     * Verifies that repository interfaces exist in domain layer.
     * Implementations should be in data layer.
     */
    @Test
    fun `repository interfaces should be in domain repository package`() {
        val repoDir = File(domainModuleRoot, "src/main/java/app/otakureader/domain/repository")
        assertTrue(
            "Domain repository package should exist at ${repoDir.absolutePath}",
            repoDir.exists() && repoDir.isDirectory
        )

        val repositoryFiles = repoDir.walkTopDown()
            .filter { it.extension == "kt" }
            .toList()

        val nonInterfaceFiles = repositoryFiles.filterNot { file ->
            file.readLines().any { it.trimStart().startsWith("interface ") }
        }

        assertTrue(
            "Domain repository package should contain only interface declarations. Non-interface files: ${nonInterfaceFiles.joinToString { it.name }}",
            repositoryFiles.isNotEmpty() && nonInterfaceFiles.isEmpty()
        )
    }

    /**
     * Verifies use cases exist in domain layer.
     * Use cases encapsulate business logic and orchestrate repository calls.
     */
    @Test
    fun `use cases should exist in domain usecase package`() {
        val useCaseDir = File(domainModuleRoot, "src/main/java/app/otakureader/domain/usecase")
        assertTrue(
            "Domain usecase package should exist at ${useCaseDir.absolutePath}",
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
     * Ensures the domain module is configured as a pure Kotlin/JVM library
     * and does not declare any Android or AndroidX dependencies.
     */
    @Test
    fun `domain build file should only declare allowed dependencies`() {
        val buildFile = File(domainModuleRoot, "build.gradle.kts")
        assertTrue(
            "Domain build.gradle.kts should exist at ${buildFile.absolutePath}",
            buildFile.exists()
        )

        val buildContent = buildFile.readText()

        // Check that it's a Kotlin library (not Android)
        assertTrue(
            "Domain should use Kotlin library plugin (not Android)",
            buildContent.contains("otakureader.kotlin.library") ||
            buildContent.contains("kotlin(\"jvm\")")
        )

        // Verify no Android dependencies (using literal string checks, not regex)
        val androidDependencyPatterns = listOf(
            "androidx.",
            "android.arch.",
            "com.google.android."
        )

        androidDependencyPatterns.forEach { pattern ->
            assertTrue(
                "Domain build.gradle.kts should not contain Android dependency: $pattern",
                !buildContent.contains(pattern)
            )
        }
    }
}
