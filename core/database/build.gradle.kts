plugins {
    alias(libs.plugins.komikku.android.library)
    alias(libs.plugins.komikku.android.room)
    alias(libs.plugins.komikku.android.hilt)
}

android {
    namespace = "app.komikku.core.database"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    api(libs.room.paging)
    implementation(libs.paging.runtime)
}
