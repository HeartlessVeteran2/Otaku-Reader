plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.hilt)
    alias(libs.plugins.otakureader.android.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.data"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.preferences)
    implementation(projects.domain)
    implementation(projects.sourceApi)

    implementation(libs.paging.runtime)
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    implementation(libs.retrofit.kotlinx.serialization)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
