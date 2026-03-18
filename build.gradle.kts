// Top-level build file — configuration common to all subprojects is in build-logic/convention plugins.
buildscript {
    repositories {
        maven("https://maven.google.com")
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.20")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.59.2")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.6")
        classpath("androidx.room:room-gradle-plugin:2.8.4")
    }
}
