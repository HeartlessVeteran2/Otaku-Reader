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
    implementation(projects.core.tachiyomiCompat)
    implementation(projects.domain)

    // Networking (OkHttp for APK downloads and extension repo fetches)
    implementation(libs.okhttp.core)

    // JSON serialization (for extension source metadata and repo index)
    implementation(libs.kotlinx.serialization.json)

    // AndroidX DataStore (preferences for extension repo configuration)
    implementation(libs.datastore.preferences)

    // AndroidX
    implementation(libs.androidx.core.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
