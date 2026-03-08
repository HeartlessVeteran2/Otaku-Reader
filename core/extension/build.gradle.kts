plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.room)
    alias(libs.plugins.otakureader.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.core.extension"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.sourceApi)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
}
