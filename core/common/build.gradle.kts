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
    api(libs.kotlinx.coroutines.core)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}
