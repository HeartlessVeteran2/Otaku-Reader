plugins {
    id("komikku.android.feature")
}

android {
    namespace = "app.komikku.feature.settings"
}

dependencies {
    implementation(project(":core:preferences"))
}
