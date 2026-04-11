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

// Security: Force secure versions of transitive dependencies
allprojects {
    configurations.all {
        resolutionStrategy {
            // Netty - fixes HTTP/2 Rapid Reset (CVE-2023-44487), MadeYouReset DDoS,
            // SSL crash (CVE-2024-29025), CRLF injection, SniHandler 16MB allocation,
            // HttpPostRequestDecoder OOM, Zip Bomb DoS, Request Smuggling (CVSS 7.5)
            force("io.netty:netty-common:4.2.12.Final")
            force("io.netty:netty-buffer:4.2.12.Final")
            force("io.netty:netty-transport:4.2.12.Final")
            force("io.netty:netty-transport-native-unix-common:4.2.12.Final")
            force("io.netty:netty-transport-native-epoll:4.2.12.Final")
            force("io.netty:netty-transport-native-kqueue:4.2.12.Final")
            force("io.netty:netty-transport-classes-epoll:4.2.12.Final")
            force("io.netty:netty-transport-classes-kqueue:4.2.12.Final")
            force("io.netty:netty-codec:4.2.12.Final")
            force("io.netty:netty-codec-base:4.2.12.Final")
            force("io.netty:netty-codec-compression:4.2.12.Final")
            force("io.netty:netty-codec-http:4.2.12.Final")
            force("io.netty:netty-codec-http2:4.2.12.Final")
            force("io.netty:netty-handler:4.2.12.Final")
            force("io.netty:netty-resolver:4.2.12.Final")

            // JDOM2 - fixes XXE injection (CVSS 7.5)
            force("org.jdom:jdom2:2.0.6.1")

            // jose4j - fixes DoS via compressed JWE (CVSS 7.5)
            force("org.bitbucket.b_c:jose4j:0.9.6")

            // Apache Commons Lang - fixes uncontrolled recursion
            force("org.apache.commons:commons-lang3:3.20.0")

            // Apache HttpClient - fixes XSS
            force("org.apache.httpcomponents:httpclient:4.5.14")
        }
    }
}
