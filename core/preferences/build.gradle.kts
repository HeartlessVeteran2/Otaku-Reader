plugins {
    alias(libs.plugins.komikku.android.library)
    alias(libs.plugins.komikku.android.hilt)
}

android {
    namespace = "app.komikku.core.preferences"
}

dependencies {
    implementation(projects.core.common)
    api(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
