plugins {
    id("otakureader.android.library")
    id("otakureader.android.hilt")
}

android {
    namespace = "app.otakureader.core.tachiyomi"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(project(":source-api"))
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:preferences"))

    // RxJava 1.x — required by the Tachiyomi extension API (Observable-based methods)
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("io.reactivex:rxandroid:1.2.1")

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
