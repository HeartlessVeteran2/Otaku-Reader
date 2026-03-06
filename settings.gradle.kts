pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Otaku-Reader"

include(
    ":app",
    ":core:common",
    ":core:network",
    ":core:database",
    ":core:preferences",
    ":core:ui",
    ":core:navigation",
    ":domain",
    ":data",
    ":source-api",
    ":feature:library",
    ":feature:reader",
    ":feature:browse",
    ":feature:updates",
    ":feature:history",
    ":feature:settings",
)
