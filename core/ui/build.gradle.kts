plugins {
    id("komikku.android.library")
    id("komikku.android.library.compose")
    id("komikku.android.hilt")
}

android {
    namespace = "app.komikku.core.ui"
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
}
