package app.otakureader

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.crash.CrashHandler
import app.otakureader.feature.reader.panel.PanelCacheService
import dagger.Lazy
import app.otakureader.shortcut.AppShortcutManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    @Inject
    lateinit var panelCacheService: Lazy<PanelCacheService>

    @Inject
    lateinit var generalPreferences: GeneralPreferences

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        // Install the crash handler first so failures during Hilt graph construction
        // or any other startup code are captured and shown on the next launch.
        CrashHandler.install(this)
        super.onCreate()
        // Enable Material You dynamic colors on Android 12+ (API 31+)
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Initialize launcher shortcuts (Library, Updates, Continue Reading)
        appShortcutManager.initialize()
    }

    // Trim Coil's memory cache when the OS signals memory pressure, preventing the
    // app from holding onto image memory that the system urgently needs elsewhere.
    // Also evicts stale panel-analysis cache entries on critical pressure.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val cache = SingletonImageLoader.get(this).memoryCache
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
                cache?.trimToSize((cache.maxSize * 0.5).toInt())
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                cache?.trimToSize(0)
                applicationScope.launch { panelCacheService.get().cleanupStaleEntries() }
            }
        }
    }

    /**
     * Configures the global Coil [ImageLoader] singleton used throughout the app.
     *
     * - Memory cache: capped at 15% of the application's available memory class with
     *   a hard ceiling of 256 MB, preventing excessive heap use on large-RAM tablets.
     * - Disk cache: capped at 512 MB to support large manga chapter image caches.
     * - allowRgb565: opaque images (most manga pages) decode as RGB_565 (2 bytes/pixel)
     *   instead of ARGB_8888 (4 bytes/pixel), halving per-page memory cost.
     * - Networking: backed by the shared [OkHttpClient] for connection pooling and
     *   consistent headers (e.g. User-Agent, Referer) set by extension interceptors.
     */
    override fun newImageLoader(context: Context): ImageLoader {
        val maxMemoryCacheBytes = minOf(
            (Runtime.getRuntime().maxMemory() * 0.15).toLong(),
            256L * 1024 * 1024
        ).toInt()
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(maxMemoryCacheBytes)
                    .build()
            }
            .diskCache {
                val diskCacheMb = runBlocking { generalPreferences.coilDiskCacheSizeMb.first() }
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(diskCacheMb.toLong() * 1024 * 1024)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .allowRgb565(true)
            .build()
    }
}
