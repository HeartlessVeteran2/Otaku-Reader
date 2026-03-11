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
        // If not set, Discord Rich Presence will be silently disabled at runtime.
        val discordAppId = project.findProperty("discordApplicationId")?.toString() ?: ""
        if (discordAppId.isBlank()) {
            logger.warn(
                "WARNING: discordApplicationId is not set. " +
                "Discord Rich Presence will not work. " +
                "Set it in local.properties or as a project property."
            )
        }
        buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordAppId\"")
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