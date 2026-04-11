plugins {
    alias(libs.plugins.otakureader.android.application)
    alias(libs.plugins.otakureader.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.otakureader"

    defaultConfig {
        applicationId = "app.otakureader"
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Full build: includes the Gemini AI SDK and all AI features.
        create("full") {
            dimension = "distribution"
        }
        // FOSS build: excludes the Gemini AI SDK; AI features are replaced with
        // no-op stubs so the app compiles and runs without any AI functionality.
        create("foss") {
            dimension = "distribution"
            applicationIdSuffix = ".foss"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.navigation)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.preferences)
    implementation(projects.core.discord)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.sourceApi)
    implementation(projects.feature.library)
    implementation(projects.feature.reader)
    implementation(projects.feature.browse)
    implementation(projects.feature.updates)
    implementation(projects.feature.history)
    implementation(projects.feature.settings)
    implementation(projects.feature.details)
    implementation(projects.feature.statistics)
    implementation(projects.feature.migration)
    implementation(projects.feature.tracking)
    implementation(projects.feature.opds)
    implementation(projects.feature.feed)

    // AI: full flavor uses the real Gemini SDK; foss flavor uses a no-op stub.
    "fullImplementation"(projects.core.ai)
    "fossImplementation"(projects.core.aiNoop)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.navigation.compose)
    implementation(libs.workmanager.ktx)
    implementation(libs.glance)
    implementation(libs.glance.material3)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // Coil: required for global SingletonImageLoader.Factory configuration
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
