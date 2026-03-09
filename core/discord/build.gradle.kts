plugins {
    alias(libs.plugins.otakureader.android.library)
    alias(libs.plugins.otakureader.android.hilt)
}

android {
    namespace = "app.otakureader.core.discord"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Register your Discord Application ID at https://discord.com/developers/applications
        // Set via local.properties or CI environment: discordApplicationId=<your_id>
        val discordAppId = project.findProperty("discordApplicationId")?.toString() ?: ""
        buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordAppId\"")
    }

    buildTypes {
        release {
            // Fail the release build if the Discord Application ID is not configured
            val discordAppId = project.findProperty("discordApplicationId")?.toString()
            if (discordAppId.isNullOrBlank()) {
                logger.warn(
                    "WARNING: discordApplicationId is not set. " +
                    "Discord Rich Presence will not work in release builds. " +
                    "Set it in local.properties or as a project property."
                )
            }
        }
    }
}

dependencies {
    implementation(projects.core.preferences)

    // Kizzy - Discord Rich Presence library
    implementation(libs.kizzy.rpc)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
