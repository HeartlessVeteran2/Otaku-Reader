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
    // ChallengeUserAgentStore lives here. Cycle-free: core:preferences depends only on
    // core:common and domain, and domain depends only on source-api.
    implementation(projects.core.preferences)

    implementation(platform(libs.okhttp.bom))
    api(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.dnsoverhttps)
    api(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
