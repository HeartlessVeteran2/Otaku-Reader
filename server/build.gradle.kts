plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("com.gradleup.shadow") version "9.4.1"
}

group = "app.otakureader"
version = "1.0.0"

dependencies {
    // D-4: Ktor dependencies now use version catalog aliases (versions managed in libs.versions.toml).
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // D-4: Logback now uses version catalog alias.
    implementation(libs.logback.classic)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("app.otakureader.server.ApplicationKt")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveVersion.set("")
        manifest {
            attributes(
                "Main-Class" to "app.otakureader.server.ApplicationKt"
            )
        }
    }
}
