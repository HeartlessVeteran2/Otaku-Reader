plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.library.compose)
    alias(libs.plugins.otakureader.android.hilt)
}

android {
    namespace = "app.otakureader.core.ui"
}

dependencies {
    implementation(projects.core.common)

    // Palette API for dynamic color extraction
    implementation(libs.androidx.palette)

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

    // Navigation animations for shared element transitions
    api(libs.accompanist.navigation.animation)

    debugImplementation(libs.compose.ui.tooling)
}
