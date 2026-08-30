import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.otakureader.android.application)
    alias(libs.plugins.otakureader.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.cyclonedx.bom)
}

android {
    namespace = "app.otakureader"

    defaultConfig {
        applicationId = "app.otakureader"
        versionCode = 2
        versionName = "0.1.0-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties().apply {
            load(FileInputStream(keystorePropertiesFile))
        }
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
        // Release-like target for macrobenchmarks (#1022): minified and shrunk like
        // release but signed with the debug key so it installs without the release
        // keystore (CI and local). Never shipped.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    // F-Droid reproducible builds: the dependency-info block is encrypted with a
    // Google Play key and makes APKs unreproducible byte-for-byte. F-Droid docs
    // require it disabled; Play does not need it for non-Play distribution.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// CycloneDX v3.x - simplified configuration
tasks.cyclonedxBom {
    includeConfigs = listOf("releaseRuntimeClasspath")
    skipConfigs = listOf("debugRuntimeClasspath", "testRuntimeClasspath")
    projectType = "application"
    schemaVersion = "1.6"
    includeLicenseText = false
    // Output defaults to build/reports/cyclonedx/bom.json
}

// Custom license report task — replaces the jk1 plugin which cannot resolve
// Android library variants under AGP 9 + Gradle 9.5 without ambiguity errors.
// Uses metadata-only resolution (resolutionResult.allComponents) so no artifact
// files are downloaded and no variant-selection ambiguity occurs.
tasks.register("generateLicenseReport") {
    group = "reporting"
    description = "Generates docs/DEPENDENCY_LICENSES.md from the release dependency graph"

    // Configuration resolution happens at execution time (allComponents is lazy), so this
    // task cannot participate in the Gradle configuration cache.
    notCompatibleWithConfigurationCache("resolves configurations at execution time")

    val outputFile = rootProject.layout.projectDirectory.file("docs/DEPENDENCY_LICENSES.md")
    outputs.file(outputFile)

    doLast {
        val components = configurations.getByName("releaseRuntimeClasspath")
            .incoming
            .resolutionResult
            .allComponents
            .mapNotNull { it.moduleVersion }
            .filter { it.group != "app.otakureader" }
            .sortedBy { "${it.group}:${it.name}" }
            .distinctBy { "${it.group}:${it.name}" }

        val output = outputFile.asFile
        val md = buildString {
            appendLine("# Otaku Reader — Dependency Licenses")
            appendLine()
            appendLine(
                "Auto-generated from the `releaseRuntimeClasspath` dependency graph. " +
                    "For full license texts see each library's repository."
            )
            appendLine()
            appendLine("| Artifact | Version |")
            appendLine("|:---------|:--------|")
            components.forEach { mv ->
                appendLine("| `${mv.group}:${mv.name}` | `${mv.version}` |")
            }
        }
        output.parentFile.mkdirs()
        output.writeText(md)
        logger.lifecycle("License report written to docs/DEPENDENCY_LICENSES.md")
    }
}

dependencies {
    // Feature modules
    implementation(projects.feature.feed)
    implementation(projects.feature.more)
    implementation(projects.feature.webview)
    implementation(projects.feature.library)
    implementation(projects.feature.reader)
    implementation(projects.feature.browse)
    implementation(projects.feature.updates)
    implementation(projects.feature.history)
    implementation(projects.feature.settings)
    implementation(projects.feature.details)
    implementation(projects.feature.statistics)
    implementation(projects.feature.migration)
    implementation(projects.feature.tracking)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.about)
    implementation(projects.feature.opds)

    // Core modules
    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.core.navigation)
    implementation(projects.core.preferences)
    implementation(projects.core.database)
    implementation(projects.core.discord)
    implementation(projects.core.webview)
    implementation(projects.core.network)
    // tachiyomi-compat exposes Injekt/NetworkHelper to extension APKs at runtime;
    // the app module needs a direct dep to call Injekt.addSingletonFactory in Application.onCreate.
    implementation(projects.core.tachiyomiCompat)
    // Needed in Application.onCreate to sweep JavaScript installs the process died in the middle
    // of (#1229) — startup is the only place that orphan can be cleaned up.
    implementation(projects.core.extension)
    implementation(projects.domain)
    implementation(projects.sourceApi)

    // Data layer (contains workers, repositories, etc.)
    implementation(projects.data)

    // Hilt WorkManager integration
    implementation(libs.hilt.work)

    // Material You dynamic colors
    implementation(libs.androidx.material)

    // AppCompat (for per-app locale support)
    implementation(libs.androidx.appcompat)

    // Biometric app lock
    implementation(libs.androidx.biometric)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)

    // WorkManager
    implementation(libs.workmanager.ktx)

    // OkHttp (backing Coil's image loader)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)

    // Coil image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Glance widgets
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // ProfileInstaller — enables on-device baseline profile installation
    implementation(libs.profileinstaller)

    // SplashScreen — branded launch experience on all API levels
    implementation(libs.splashscreen)

    // Encrypted SharedPreferences for crash log storage
    implementation(libs.androidx.security.crypto)

    // Crash reporting (#952) — Sentry SDK, configured at runtime via user-supplied DSN
    implementation(libs.sentry.android)

    // Debug tools (LeakCanary — no-op in release builds)
    debugImplementation(libs.leakcanary)

    // Activity Compose
    implementation(libs.androidx.activity.compose)
}
