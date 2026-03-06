plugins {
    id("komikku.android.library")
}

android {
    namespace = "app.komikku.core.navigation"
}

dependencies {
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
