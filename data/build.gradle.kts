plugins {
    id("komikku.android.library")
    id("komikku.android.hilt")
}

android {
    namespace = "app.komikku.data"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(project(":source-api"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
