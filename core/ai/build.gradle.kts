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

    // ML Kit for text recognition and image labeling
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.image.labeling)

    // DataStore (used by AiConfigManager)
    implementation(libs.datastore.preferences)

    // Encrypted storage (used by SecureApiKeyDataStore)
    implementation(libs.androidx.security.crypto)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
