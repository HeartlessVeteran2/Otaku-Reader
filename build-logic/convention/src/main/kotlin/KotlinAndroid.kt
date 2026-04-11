import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Configures common Kotlin/Android settings for both application and library modules.
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension
) {
    commonExtension.apply {
        compileSdk = 36

        defaultConfig.minSdk = 26
        
        // H-10: Explicitly declare targetSdk for application modules so the app does not
        // inherit an outdated default, which would cause Play Store rejection and missing
        // behavioral changes. Library modules don't have targetSdk in their defaultConfig.
        // Keep this in sync with compileSdk unless there is a specific reason to target
        // an older API level (e.g. a breaking behavior change in the new SDK).
        if (commonExtension is ApplicationExtension) {
            (commonExtension as ApplicationExtension).defaultConfig.targetSdk = 35
        }

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            isCoreLibraryDesugaringEnabled = true
        }
    }

    extensions.configure(KotlinAndroidProjectExtension::class.java) {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=kotlinx.coroutines.FlowPreview"
                )
            )
        }
    }

    dependencies.add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:2.1.4")
}
