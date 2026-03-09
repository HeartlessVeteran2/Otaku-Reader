package app.otakureader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Otaku Reader.
 * Initializes Hilt dependency injection, WorkManager with Hilt integration,
 * and Material You dynamic colors for Android 12+.
 */
@HiltAndroidApp
class OtakuReaderApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Enable Material You dynamic colors on Android 12+ (API 31+)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
