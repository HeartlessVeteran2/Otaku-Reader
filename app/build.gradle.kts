plugins {
    id("komikku.android.application")
    id("komikku.android.hilt")
}

android {
    namespace = "app.komikku"
    defaultConfig {
        applicationId = "app.komikku"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature:library"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:browse"))
    implementation(project(":feature:updates"))
    implementation(project(":feature:history"))
    implementation(project(":feature:settings"))
}
