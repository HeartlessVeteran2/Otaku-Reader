// Top-level build file — configuration common to all subprojects is in build-logic/convention plugins.
buildscript {
    repositories {
        maven("https://maven.google.com")
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.59.2")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.6")
    }
}
