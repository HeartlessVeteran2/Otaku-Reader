plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.feature.reader"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(projects.core.ui)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(libs.paging.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
}
