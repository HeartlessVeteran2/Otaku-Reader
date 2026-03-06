plugins {
    alias(libs.plugins.komikku.android.library)
    alias(libs.plugins.komikku.android.hilt)
    alias(libs.plugins.komikku.android.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.komikku.data"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.preferences)
    implementation(projects.domain)
    implementation(projects.sourceApi)

    implementation(libs.paging.runtime)
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
}
