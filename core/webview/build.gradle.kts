plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.library.compose)
    alias(libs.plugins.otakureader.android.hilt)
}

android {
    namespace = "app.otakureader.core.webview"
}

dependencies {
    // @ApplicationScope lives here. Not transitive through core:preferences, which exposes
    // core:common as `implementation` rather than `api`.
    implementation(projects.core.common)
    implementation(projects.core.navigation)
    implementation(projects.core.preferences)
    // Implements CloudflareChallengeSolver, declared there so :core:network needs no UI dep.
    implementation(projects.core.network)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.material.icons.extended)
    // Cookie syncing between WebView's CookieManager and OkHttp's CookieJar
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
