plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.hilt)
    alias(libs.plugins.otakureader.android.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.data"

    // Mirror the flavor dimension from :app so that flavor-specific source sets
    // (full/foss) compile correctly and the Gemini SDK is excluded in foss builds.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.preferences)
    // core:ai is only needed for the full flavor; foss uses core:ai-noop via :app.
    "fullImplementation"(projects.core.ai)
    implementation(projects.domain)
    implementation(projects.sourceApi)

    implementation(libs.paging.runtime)
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.coil.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    // AiRepositoryImplTest needs GeminiClient on the classpath (it mocks it).
    "testFullImplementation"(projects.core.ai)
}
