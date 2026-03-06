plugins {
    id("komikku.android.feature")
}

android {
    namespace = "app.komikku.feature.reader"
}

dependencies {
    implementation(libs.coil.compose)
}
