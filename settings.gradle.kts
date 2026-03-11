enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        maven("https://maven.google.com")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://maven.google.com")
        maven("https://jitpack.io")
    }
}

rootProject.name = "Otaku-Reader"

// App
include(":app")

// Build logic is an includeBuild, not a regular module

// Core modules
include(":core:common")
include(":core:network")
include(":core:database")
include(":core:preferences")
include(":core:ui")
include(":core:navigation")
include(":core:extension")
include(":core:tachiyomi-compat")
include(":core:discord")

// Domain layer
include(":domain")

// Data layer
include(":data")

// Source API
include(":source-api")

// Feature modules
include(":feature:library")
include(":feature:reader")
include(":feature:browse")
include(":feature:updates")
include(":feature:history")
include(":feature:settings")
include(":feature:details")
include(":feature:statistics")
include(":feature:migration")
include(":feature:tracking")
include(":feature:onboarding")
