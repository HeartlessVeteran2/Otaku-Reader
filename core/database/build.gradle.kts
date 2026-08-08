plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.room)
    alias(libs.plugins.otakureader.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "app.otakureader.core.database"

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets.getByName("test") {
        assets.srcDirs(files("schemas"))
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(libs.kotlinx.serialization.json)
    api(libs.room.paging)
    implementation(libs.paging.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
}

kover {
    reports {
        verify {
            rule {
                // Ratchet floor from measured coverage (Kover 0.9.8, 2026-06-10). The previous 60% gate passed vacuously because Kover 0.8.x could not instrument AGP 9 modules. Raise this as coverage improves; never lower it.
                minBound(25)
            }
        }
    }
}
