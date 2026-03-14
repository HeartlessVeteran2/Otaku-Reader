plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.core.network"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.common)

    api(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    api(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)
}
