import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.the
import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Convention plugin for modules using Hilt dependency injection.
 * Applies KSP and Hilt Gradle plugins and adds Hilt compiler dependency.
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("com.google.devtools.ksp")
                apply("com.google.dagger.hilt.android")
            }
            val libs = the<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("implementation", libs.findLibrary("hilt.android").get())
                add("ksp", libs.findLibrary("hilt.compiler").get())
            }

            // Hilt's annotation processor reads the Kotlin metadata of every class it scans,
            // using kotlin-metadata-jvm — and Hilt 2.59.2 pulls in 2.2.20, which refuses any
            // metadata newer than 2.3.0 rather than degrading gracefully.
            //
            // That is a problem the moment a *dependency* is built with a newer Kotlin than we
            // are. quickjs-kt 1.0.9 ships metadata 2.4.0, so processing dies with "Provided
            // Metadata instance has version 2.4.0" on any module whose classpath reaches it —
            // a failure with no connection to the code being compiled, which makes it hard to
            // place if you have not seen it before.
            //
            // Pinning the reader to our own Kotlin version fixes it: a reader from Kotlin
            // 2.3.21 accepts metadata through 2.4.0. Applied here rather than in one module so
            // it cannot be reintroduced by the next dependency that happens to be built ahead
            // of us; it tracks `kotlin` in the version catalog and needs no attention on upgrade.
            //
            // Scoped to the annotation-processing classpaths, not every configuration. This is a
            // workaround for one tool, and forcing it globally would silently rewrite the
            // runtime and tooling graphs too — so a future dependency that legitimately needs a
            // different metadata-reader version would fail somewhere with no connection to Hilt.
            // A build-tool problem should stay inside the build tool's classpath.
            configurations
                .matching { it.name.startsWith("ksp") || it.name.startsWith("kapt") ||
                    it.name.contains("annotationProcessor", ignoreCase = true) ||
                    it.name.startsWith("hilt") }
                .configureEach {
                    resolutionStrategy {
                        force(libs.findLibrary("kotlin.metadata.jvm").get())
                    }
                }
        }
    }
}
