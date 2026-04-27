plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.feature.settings"

    // Mirror the flavor dimension from :app and :data so Gradle can match variants
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "AI_FEATURES_AVAILABLE", "true")
        }
        create("foss") {
            dimension = "distribution"
            buildConfigField("boolean", "AI_FEATURES_AVAILABLE", "false")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(projects.core.discord)
    implementation(projects.data)
    "fullImplementation"(libs.play.services.auth)
    // Note: feature.reader was removed as a dependency here. The shared types
    // (ImageQuality, ReaderMode, etc.) now come from :domain and ReaderSettingsRepository
    // comes from :data, which are both already included via the feature convention plugin.
    implementation(libs.paging.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
    // T-1: Unit test dependencies for AiKeyValidationTest and SettingsViewModelTest
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
