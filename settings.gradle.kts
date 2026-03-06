enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.dagger.*")
                includeGroupByRegex("com\\.google\\.devtools.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
                includeGroupByRegex("com\\.google\\.gms.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.dagger.*")
                includeGroupByRegex("com\\.google\\.devtools.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
                includeGroupByRegex("com\\.google\\.gms.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

// Baseline profile generator
include(":baselineprofile")
