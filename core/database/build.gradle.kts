plugins {
    id("komikku.android.library")
    id("komikku.android.room")
}

android {
    namespace = "app.komikku.core.database"
}

dependencies {
    implementation(project(":core:common"))
}
