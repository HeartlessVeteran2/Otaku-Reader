plugins {
    alias(libs.plugins.komikku.android.library)
}

android {
    namespace = "app.komikku.core.common"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.coroutines.core)
}
