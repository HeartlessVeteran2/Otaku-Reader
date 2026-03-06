plugins {
    id("komikku.android.library")
}

android {
    namespace = "app.komikku.core.preferences"
}

dependencies {
    implementation(project(":core:common"))
}
