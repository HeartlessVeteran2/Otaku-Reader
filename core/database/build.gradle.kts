plugins {
    id("komikku.android.library")
    id("komikku.android.hilt")
    id("komikku.android.room")
}

android {
    namespace = "app.komikku.core.database"
}

dependencies {
    testImplementation(libs.junit)
}
