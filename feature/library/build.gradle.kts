plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "app.otakureader.feature.library"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.preferences)
    implementation(projects.sourceApi)
    implementation(libs.paging.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.workmanager.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Screenshot tests
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(libs.robolectric)
}
