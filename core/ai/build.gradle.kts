plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.hilt)
}

android {
    namespace = "app.otakureader.core.ai"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.common)

    // Gemini AI SDK
    api(libs.generativeai)

    // ML Kit for on-device text recognition and image labeling
    // Note: These dependencies increase APK size and require Google Play Services.
    // Consider dynamic feature modules if only needed in specific app flows.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.image.labeling)

    // Firebase BoM ensures compatible versions across all Firebase dependencies
    implementation(platform(libs.firebase.bom))
    // Firestore is used for AI data storage/sync (version managed by BoM)
    implementation(libs.firebase.firestore.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
