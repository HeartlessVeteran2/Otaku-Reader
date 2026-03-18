plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "app.otakureader"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor (updated to 3.1.2 for security fixes)
    implementation("io.ktor:ktor-server-core:3.1.2")
    implementation("io.ktor:ktor-server-netty:3.1.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")
    implementation("io.ktor:ktor-server-auth:3.1.2")
    implementation("io.ktor:ktor-server-status-pages:3.1.2")
    implementation("io.ktor:ktor-server-call-logging:3.1.2")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.18")
    
    // Testing
    testImplementation("io.ktor:ktor-server-test-host:3.1.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.10")
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
