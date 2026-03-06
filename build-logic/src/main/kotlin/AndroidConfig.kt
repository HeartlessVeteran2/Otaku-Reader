import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

internal fun Project.libsCatalog(): VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.configureAndroidCommon(
    extension: CommonExtension<*, *, *, *, *, *>,
    withCompose: Boolean = false
) {
    val libs = libsCatalog()
    val compileSdk = libs.findVersion("android-compile-sdk").get().requiredVersion.toInt()
    val minSdk = libs.findVersion("android-min-sdk").get().requiredVersion.toInt()
    extension.apply {
        this.compileSdk = compileSdk
        defaultConfig {
            this.minSdk = minSdk
        }
        if (this is ApplicationExtension) {
            defaultConfig {
                targetSdk = libs.findVersion("android-target-sdk").get().requiredVersion.toInt()
                versionCode = 1
                versionName = "1.0"
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        packaging {
            resources.excludes.addAll(
                listOf(
                    "META-INF/AL2.0",
                    "META-INF/LGPL2.1"
                )
            )
        }
        if (withCompose) {
            buildFeatures.compose = true
            composeOptions.kotlinCompilerExtensionVersion =
                libs.findVersion("compose-compiler").get().requiredVersion
        }
    }

    (this as Project).extensions.findByName("kotlinOptions")?.let {
        (it as KotlinJvmOptions).jvmTarget = "17"
    }
}
