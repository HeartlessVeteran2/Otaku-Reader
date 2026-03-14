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

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
