plugins {
    id("komikku.android.library")
    id("komikku.android.hilt")
}

android {
    namespace = "app.komikku.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
