// Top-level build file — configuration common to all subprojects is in build-logic/convention plugins.
buildscript {
    repositories {
        maven("https://maven.google.com")
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.51.1")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.21-1.0.28")
    }
}
