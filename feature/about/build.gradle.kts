plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.feature.about"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.navigation)
    implementation(projects.core.preferences)
    implementation(libs.kotlinx.serialization.json)
}