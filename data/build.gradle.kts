plugins {
    id("komikku.android.library")
    id("komikku.android.hilt")
}

android {
    namespace = "app.komikku.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(project(":domain"))
    implementation(project(":source-api"))
}
