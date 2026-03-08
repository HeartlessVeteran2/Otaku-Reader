plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.room)
    alias(libs.plugins.otakureader.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

android {
    namespace = "app.otakureader.core.extension"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.sourceApi)
    implementation(projects.core.tachiyomiCompat)

    implementation(libs.androidx.core.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
}
