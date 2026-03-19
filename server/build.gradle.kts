plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("com.gradleup.shadow") version "9.4.0"
}

group = "app.otakureader"
version = "1.0.0"

dependencies {
    // Ktor
    implementation("io.ktor:ktor-server-core:3.4.1")
    implementation("io.ktor:ktor-server-netty:3.4.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.4.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.1")
    implementation("io.ktor:ktor-server-auth:3.4.1")
    implementation("io.ktor:ktor-server-status-pages:3.4.1")
    implementation("io.ktor:ktor-server-call-logging:3.4.1")
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.32")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:3.4.1")
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
