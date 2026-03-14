plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.feature.settings"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(projects.core.discord)
    implementation(projects.data)
    implementation(projects.feature.reader)
    implementation(libs.paging.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
}
