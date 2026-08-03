plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.room)
    alias(libs.plugins.otakureader.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

android {
    namespace = "app.otakureader.core.extension"

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        // Without this, any android.jar method a test reaches throws "not mocked" instead of
        // returning a default — so the failure-logging inside the remote data source, and the
        // broadcast the uninstall path sends, made their code paths untestable rather than
        // failing. Matches what :core:js-runtime already sets.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(projects.domain)
    implementation(projects.sourceApi)
    implementation(projects.core.tachiyomiCompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
