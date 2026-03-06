plugins {
    alias(libs.plugins.komikku.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.komikku.feature.updates"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(libs.paging.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
}
