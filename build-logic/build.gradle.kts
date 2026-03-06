plugins {
    `kotlin-dsl`
}

group = "app.komikku.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.7.3")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    compileOnly("org.jetbrains.kotlin:kotlin-serialization:2.0.21")
    compileOnly("com.google.dagger:hilt-android-gradle-plugin:2.52")
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.0.21-1.0.25")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "komikku.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "komikku.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "komikku.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "komikku.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "komikku.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "komikku.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
    }
}
