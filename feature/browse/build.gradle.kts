plugins {
    id("komikku.android.feature")
}

android {
    namespace = "app.komikku.feature.browse"
}

dependencies {
    implementation(project(":source-api"))
}
