plugins {
    alias(libs.plugins.komikku.android.library)
    alias(libs.plugins.komikku.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.komikku.core.network"
}

dependencies {
    implementation(projects.core.common)

    api(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    api(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)
}
