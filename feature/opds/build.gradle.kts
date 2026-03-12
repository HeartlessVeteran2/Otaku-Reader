plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.feature.opds"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
}
