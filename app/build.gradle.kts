plugins {
    alias(libs.plugins.otakureader.android.application)
    alias(libs.plugins.otakureader.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader"

    defaultConfig {
        applicationId = "app.otakureader"
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // Mirror the distribution flavor dimension from :data so variant resolution works
    flavorDimensions += "distribution"
    productFlavors {
        create("full") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
    }
}

dependencies {
    // Feature modules
    implementation(projects.feature.feed)
    implementation(projects.feature.more)
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
    implementation(projects.domain)
    implementation(projects.sourceApi)

    // Data layer (contains workers, repositories, etc.)
    implementation(projects.data)

    // AI: full flavor uses real Gemini client, foss uses no-op
    "fullImplementation"(projects.core.ai)
    "fossImplementation"(projects.core.aiNoop)

    // Hilt WorkManager integration
    implementation(libs.hilt.work)

    // Material You dynamic colors
    implementation(libs.androidx.material)

    // AppCompat (for per-app locale support)
    implementation(libs.androidx.appcompat)

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

    // Activity Compose
    implementation(libs.androidx.activity.compose)
}
