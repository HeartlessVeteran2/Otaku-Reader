plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.hilt)
}

android {
    namespace = "app.otakureader.core.tachiyomi"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(projects.core.network)

    // RxJava 1.x — required by the Tachiyomi extension API (Observable-based methods)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)

    // OkHttp (shared with Tachiyomi extensions)
    implementation(libs.okhttp.core)

    // Note: eu.kanade.tachiyomi.source.* types are provided by local stubs
    // in src/main/java/eu/kanade/tachiyomi/ — no external extensions-lib needed.
    // Note: org.xmlpull.v1.* is provided by the Android SDK — no standalone dep needed.

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
