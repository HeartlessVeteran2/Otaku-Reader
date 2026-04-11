plugins {
    alias(libs.plugins.otakureader.android.feature)
}

android {
    namespace = "app.otakureader.feature.statistics"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(libs.lifecycle.viewmodel.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
