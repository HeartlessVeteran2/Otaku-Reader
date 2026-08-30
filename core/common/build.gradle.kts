plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.hilt)
}

android {
    namespace = "app.otakureader.core.common"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.palette)
    implementation(libs.kotlinx.coroutines.android)
    // For Call.await() — the cancellable bridge in net/CallExtensions.kt (#1231). `compileOnly`
    // because every consumer already declares its own OkHttp; this module only needs the types to
    // compile against and must not force a second copy onto anyone's runtime classpath.
    compileOnly(libs.okhttp.core)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
