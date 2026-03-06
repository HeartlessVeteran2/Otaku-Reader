plugins {
    id("komikku.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.komikku.core.network"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
}
