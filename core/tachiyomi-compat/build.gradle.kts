plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.hilt)
}

android {
    namespace = "app.otakureader.core.tachiyomi"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(projects.core.network)

    // RxJava 1.x — required by the Tachiyomi extension API (Observable-based methods).
    // `api` so loaded extension APKs (compiled against extensions-lib's rx.* types) see
    // the same rx.Observable classes the host's HttpSource exposes.
    api(libs.rxjava)
    api(libs.rxandroid)

    // OkHttp (shared with Tachiyomi extensions) — `api` so extensions' okhttp3.* references
    // resolve against the host's classes.
    api(libs.okhttp.core)

    // Jsoup — referenced by ParsedHttpSource / many extensions for HTML parsing.
    api(libs.jsoup)

    // Injekt (mihonapp fork) — extensions call Injekt.get<NetworkHelper>() / injectLazy().
    api(libs.injekt)

    // androidx.preference — backs the ConfigurableSource.setupPreferenceScreen(PreferenceScreen)
    // contract (PreferenceScreen = androidx.preference.PreferenceScreen).
    api(libs.androidx.preference)

    // kotlinx.serialization Json — Response.parseAs() and the host Injekt-provided Json.
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    // The eu.kanade.tachiyomi.* host contract (Source/HttpSource/network helpers/models)
    // is defined in this module's src/main/java/eu/kanade/tachiyomi/ and exposed to
    // extension APKs at runtime via the host classloader.
    // Note: org.xmlpull.v1.* is provided by the Android SDK — no standalone dep needed.

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
