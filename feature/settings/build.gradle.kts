plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "app.otakureader.feature.settings"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    // NetworkSettings and WebViewCookieJar back the Advanced screen's network section.
    implementation(projects.core.network)
    implementation(projects.core.discord)
    implementation(libs.paging.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
    // T-1: Unit test dependencies for AiKeyValidationTest and SettingsViewModelTest
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

kover {
    reports {
        verify {
            rule {
                // Ratchet floor from measured coverage (Kover 0.9.8, 2026-06-10). The previous 60% gate passed vacuously because Kover 0.8.x could not instrument AGP 9 modules. Raise this as coverage improves; never lower it.
                minBound(5)
            }
        }
    }
}
