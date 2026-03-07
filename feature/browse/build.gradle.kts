plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.feature.browse"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(projects.core.extension)
    implementation(projects.core.tachiyomiCompat)
    implementation(projects.domain)
    implementation(projects.sourceApi)
    implementation(libs.paging.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
}
