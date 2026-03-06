plugins {
    id("komikku.android.library")
    id("komikku.android.hilt")
}

android {
    namespace = "app.komikku.core.preferences"
}

dependencies {
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
