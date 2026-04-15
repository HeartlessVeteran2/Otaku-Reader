// Top-level build file — configuration common to all subprojects is in build-logic/convention plugins.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    // R-4: detekt static analysis applied to all subprojects
    alias(libs.plugins.detekt)
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    source.setFrom(
        "app/src",
        "core",
        "data/src",
        "domain/src",
        "feature",
        "server/src"
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/build/**", "**/generated/**")
}

// Security: Force secure versions of transitive dependencies
allprojects {
    configurations.all {
        resolutionStrategy {
            // Netty - fixes HTTP/2 Rapid Reset (CVE-2023-44487), MadeYouReset DDoS,
            // SSL crash (CVE-2024-29025), CRLF injection, SniHandler 16MB allocation,
            // HttpPostRequestDecoder OOM, Zip Bomb DoS, Request Smuggling (CVSS 7.5),
            // CVE-2025-59419 (HTTP/2 CONTINUATION flood), CVE-2025-67735 (buffer handling)
            val nettyVersion = libs.versions.netty.get()
            force("io.netty:netty-common:$nettyVersion")
            force("io.netty:netty-buffer:$nettyVersion")
            force("io.netty:netty-transport:$nettyVersion")
            force("io.netty:netty-transport-native-unix-common:$nettyVersion")
            force("io.netty:netty-transport-native-epoll:$nettyVersion")
            force("io.netty:netty-transport-native-kqueue:$nettyVersion")
            force("io.netty:netty-transport-classes-epoll:$nettyVersion")
            force("io.netty:netty-transport-classes-kqueue:$nettyVersion")
            force("io.netty:netty-codec:$nettyVersion")
            force("io.netty:netty-codec-base:$nettyVersion")
            force("io.netty:netty-codec-compression:$nettyVersion")
            force("io.netty:netty-codec-http:$nettyVersion")
            force("io.netty:netty-codec-http2:$nettyVersion")
            force("io.netty:netty-handler:$nettyVersion")
            force("io.netty:netty-resolver:$nettyVersion")

            // JDOM2 - fixes XXE injection (CVSS 7.5)
            force("org.jdom:jdom2:2.0.6.1")

            // jose4j - fixes DoS via compressed JWE (CVSS 7.5)
            force("org.bitbucket.b_c:jose4j:0.9.6")

            // Apache Commons Lang - fixes uncontrolled recursion
            force("org.apache.commons:commons-lang3:3.20.0")

            // Apache HttpClient - fixes XSS
            force("org.apache.httpcomponents:httpclient:4.5.14")

            // Log4j Core - fixes XMLLayout sanitization and RCE (CVE-2021-44228); bumped to 2.25.4
            force("org.apache.logging.log4j:log4j-core:2.25.4")

            // Plexus Utils - fixes directory traversal (CVSS 7.5)
            force("org.codehaus.plexus:plexus-utils:4.0.3")
        }
    }
}
