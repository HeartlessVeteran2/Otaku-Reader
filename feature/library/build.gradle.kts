plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader.feature.library"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { testTask ->
                testTask.systemProperty(
                    "roborazzi.output.dir",
                    "${project.projectDir}/src/test/screenshots",
                )
                // Forward -Proborazzi.test.record=true / -Proborazzi.test.verify=true from CLI
                listOf("roborazzi.test.record", "roborazzi.test.verify").forEach { prop ->
                    if (project.hasProperty(prop)) {
                        testTask.systemProperty(prop, project.property(prop)!!)
                    }
                }
            }
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.extension)
    implementation(projects.core.preferences)
    implementation(projects.sourceApi)
    implementation(libs.paging.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.workmanager.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Screenshot tests
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(libs.robolectric)

    // Compose UI tests on JVM (Robolectric)
    testImplementation(libs.compose.ui.test.junit4)
}
