plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.hilt)
}

android {
    namespace = "app.otakureader.core.js"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // JsSource implements the app's own MangaSource contract, so nothing downstream of
    // SourceRepository needs to know a source is backed by JavaScript rather than an APK.
    implementation(projects.sourceApi)
    implementation(projects.core.common)
    implementation(projects.core.preferences)

    // The QuickJS engine. Confined to this module on purpose — see libs.versions.toml.
    implementation(libs.quickjs)

    // HTML parsing for the `Document` global exposed to JS sources. Already a dependency of
    // the APK extension path, so this adds no new transitive weight.
    implementation(libs.jsoup)

    // JS sources issue HTTP through the app's shared client so rate limiting, certificate
    // pinning and the cookie jar apply to them exactly as they do to everything else.
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
