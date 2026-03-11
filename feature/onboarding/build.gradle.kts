plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.feature.onboarding"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(libs.kotlinx.serialization.json)
}