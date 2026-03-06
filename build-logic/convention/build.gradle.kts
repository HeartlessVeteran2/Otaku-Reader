import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "app.komikku.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.plugins.android.application.get().run {
        "$pluginId:$pluginId.gradle.plugin:${version.requiredVersion}"
    })
    compileOnly(libs.plugins.android.library.get().run {
        "$pluginId:$pluginId.gradle.plugin:${version.requiredVersion}"
    })
    compileOnly(libs.plugins.kotlin.android.get().run {
        "$pluginId:$pluginId.gradle.plugin:${version.requiredVersion}"
    })
    compileOnly(libs.plugins.kotlin.compose.get().run {
        "$pluginId:$pluginId.gradle.plugin:${version.requiredVersion}"
    })
    compileOnly(libs.plugins.ksp.get().run {
        "$pluginId:$pluginId.gradle.plugin:${version.requiredVersion}"
    })
    compileOnly(libs.plugins.hilt.get().run {
        "$pluginId:$pluginId.gradle.plugin:${version.requiredVersion}"
    })
    compileOnly(libs.plugins.room.get().run {
        "$pluginId:$pluginId.gradle.plugin:${version.requiredVersion}"
    })
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "komikku.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "komikku.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "komikku.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "komikku.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "komikku.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "komikku.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
    }
}
