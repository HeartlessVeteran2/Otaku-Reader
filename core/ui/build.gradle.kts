plugins {
    alias(libs.plugins.komikku.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.komikku.core.ui"
    buildFeatures { compose = true }
}

dependencies {
    implementation(projects.core.common)

    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    api(libs.compose.foundation)
    api(libs.compose.runtime)

    api(libs.lifecycle.viewmodel.compose)
    api(libs.lifecycle.runtime.compose)

    api(libs.coil.compose)
    api(libs.coil.okhttp)

    debugImplementation(libs.compose.ui.tooling)
}
