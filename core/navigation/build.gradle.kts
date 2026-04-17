plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.core.navigation"
}

dependencies {
    api(libs.navigation.compose)
    api(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
