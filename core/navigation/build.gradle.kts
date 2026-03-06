plugins {
    alias(libs.plugins.komikku.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.komikku.core.navigation"
}

dependencies {
    api(libs.navigation.compose)
    api(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
