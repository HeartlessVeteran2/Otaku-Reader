plugins {
    alias(libs.plugins.komikku.android.library)
}

android {
    namespace = "app.komikku.core.navigation"
}

dependencies {
    api(libs.navigation.compose)
    api(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
