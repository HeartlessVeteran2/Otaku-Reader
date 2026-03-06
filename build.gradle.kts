import app.komikku.buildlogic.libs

// Top-level build file for Komikku
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

buildscript {
    dependencies {
        classpath(libs.android.gradlePlugin)
        classpath(libs.kotlin.gradlePlugin)
        classpath(libs.ksp.gradlePlugin)
    }
}

allprojects {
    configurations.all {
        resolutionStrategy {
            // Force consistent versions
            force(libs.kotlinx.coroutines.core)
            force(libs.kotlinx.coroutines.android)
            
            cacheChangingModulesFor(0, TimeUnit.SECONDS)
            cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
        }
    }
}

subprojects {
    // Apply ktlint to all subprojects
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        verbose.set(true)
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
