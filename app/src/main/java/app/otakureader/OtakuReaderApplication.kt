package app.otakureader

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.otakureader.shortcut.AppShortcutManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

/**
 * Application class for Otaku Reader.
 * Initializes Hilt dependency injection, WorkManager with Hilt integration,
 * Material You dynamic colors for Android 12+, launcher shortcuts, and the global Coil ImageLoader
 * with memory/disk cache limits and OkHttp networking.
 */
@HiltAndroidApp
class OtakuReaderApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appShortcutManager: AppShortcutManager

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Enable Material You dynamic colors on Android 12+ (API 31+)
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Initialize launcher shortcuts (Library, Updates, Continue Reading)
        appShortcutManager.initialize()
    }

    /**
     * Configures the global Coil [ImageLoader] singleton used throughout the app.
     *
     * - Memory cache: capped at 25% of the application's available memory class
     *   (derived from device RAM via [android.app.ActivityManager.memoryClass]) to
     *   prevent OOM crashes during rapid manga reading.
     * - Disk cache: capped at 512 MB to support large manga chapter image caches.
     * - Networking: backed by the shared [OkHttpClient] for connection pooling and
     *   consistent headers (e.g. User-Agent, Referer) set by extension interceptors.
     * - Crossfade: smooth image transitions in the UI.
     */
    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .crossfade(true)
            .build()
    }
}
