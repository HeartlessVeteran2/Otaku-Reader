package app.otakureader

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.otakureader.core.preferences.CrashReportingStore
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.crash.CrashHandler
import app.otakureader.crash.CrashReporter
import app.otakureader.domain.scheduler.DownloadFolderMigrationScheduler
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import app.otakureader.shortcut.AppShortcutManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import app.otakureader.core.network.RequestCategory
import app.otakureader.core.network.di.PageImageOkHttp
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import app.otakureader.core.extension.installer.ExtensionInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for Otaku Reader.
 * Initializes Hilt dependency injection, WorkManager with Hilt integration,
 * Material You dynamic colors for Android 12+, launcher shortcuts, and the global Coil ImageLoader
 * with memory/disk cache limits and OkHttp networking.
 */
@HiltAndroidApp
class OtakuReaderApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    companion object {
        /** Fraction of the memory cache to retain when the UI is hidden. */
        private const val TRIM_MEMORY_UI_HIDDEN_FACTOR = 0.5
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appShortcutManager: AppShortcutManager

    @Inject
    lateinit var okHttpClient: OkHttpClient

    /**
     * The page-image client, used only for Coil.
     *
     * Kept distinct from [okHttpClient], which is what extensions get through Injekt below.
     * This one attaches source-supplied page headers, and handing that to extension code would
     * let one source obtain another's credentials by requesting its image URLs.
     */
    @Inject
    @PageImageOkHttp
    lateinit var pageImageOkHttpClient: OkHttpClient


    @Inject
    lateinit var generalPreferences: GeneralPreferences

    @Inject
    lateinit var downloadFolderMigrationScheduler: DownloadFolderMigrationScheduler

    @Inject
    lateinit var extensionInstaller: ExtensionInstaller

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Earliest point we control — runs BEFORE every ContentProvider (Sentry's
        // SentryInitProvider/SentryPerformanceProvider, FileProvider, androidx.startup
        // InitializationProvider) executes its onCreate. Installing the crash handler here
        // means a crash in any of those providers is captured to Downloads/prefs instead of
        // killing the process invisibly. onCreate later re-calls install() to attach the
        // Sentry store; install() is idempotent.
        try {
            CrashHandler.install(base)
        } catch (_: Throwable) {
            // Never let diagnostics setup prevent the app from starting.
        }
    }

    override fun onCreate() {
        // Read crash-reporting prefs directly (no Hilt) since CrashHandler installs BEFORE
        // super.onCreate() and the Hilt graph isn't constructed yet at that point. The store
        // is keystore-backed encrypted SharedPreferences, safe to instantiate here. (#952)
        val crashReportingStore = CrashReportingStore(this)
        // Init Sentry first so the captureException() inside CrashHandler has a live SDK.
        CrashReporter.initialize(this, crashReportingStore)
        // Install the crash handler first so failures during Hilt graph construction
        // or any other startup code are captured and shown on the next launch.
        CrashHandler.install(this, crashReportingStore)
        super.onCreate()
        // Post-DI initialization is wrapped so a failure in optional startup work (dynamic
        // color registration, launcher-shortcut sync) can never crash the process before
        // the first Activity opens. Each is non-essential to launching the app.
        // Bootstrap Injekt so loaded extension APKs can resolve their host dependencies via
        // injectLazy() / Injekt.get(). Must run after super.onCreate() so the Hilt graph is
        // ready (okHttpClient is injected by Hilt). All registrations are lazy singletons —
        // the factory runs once on first get() and the result is cached.
        //
        // Injekt is a service locator, so anything an extension asks for at runtime must have
        // been put in the map first; a missing entry throws rather than failing to compile.
        // Application is required by ConfigurableSource.getSourcePreferences() — see
        // core/tachiyomi-compat/.../ConfigurableSource.kt. Many real extensions read their
        // source preferences from a constructor or a `baseUrl` getter, so without this binding
        // they throw during instantiation and the loader reports the extension as having no
        // valid sources. That presented as the app simply having no sources at all.
        Injekt.addSingletonFactory<Application> { this }
        Injekt.addSingletonFactory<NetworkHelper> { NetworkHelper(applicationContext, baseClient = okHttpClient) }
        Injekt.addSingletonFactory<Json> {
            Json {
                isLenient = true
                ignoreUnknownKeys = true
            }
        }

        try {
            // Enable Material You dynamic colors on Android 12+ (API 31+)
            DynamicColors.applyToActivitiesIfAvailable(this)
        } catch (e: Throwable) {
            android.util.Log.e("OtakuReaderApp", "DynamicColors init failed", e)
        }
        try {
            // Initialize launcher shortcuts (Library, Updates, Continue Reading)
            appShortcutManager.initialize()
        } catch (e: Throwable) {
            android.util.Log.e("OtakuReaderApp", "App shortcut init failed", e)
        }
        try {
            // One-time migration of download folders from numeric sourceId to source display
            // name; the underlying worker checks GeneralPreferences.downloadFolderMigrationDone
            // and no-ops if already run.
            downloadFolderMigrationScheduler.schedule()
        } catch (e: Throwable) {
            android.util.Log.e("OtakuReaderApp", "Download folder migration enqueue failed", e)
        }
        // Remove JavaScript scripts left behind by an install the process died in the middle of
        // (#1229). Startup is the only place this can run: the orphan exists precisely because
        // nothing in the previous process lived long enough to clean it up. Off the main thread
        // and failure-tolerant — it touches disk, and it must never delay or break launch.
        applicationScope.launch {
            try {
                val removed = extensionInstaller.sweepInterruptedJsInstalls()
                if (removed > 0) {
                    android.util.Log.i("OtakuReaderApp", "Removed \$removed orphaned JS install(s)")
                }
            } catch (e: Throwable) {
                android.util.Log.e("OtakuReaderApp", "Interrupted-install sweep failed", e)
            }
        }
    }

    /**
     * Scope for startup work that must outlive no particular screen.
     *
     * `SupervisorJob` so one failing task cannot cancel the others, and it is never cancelled —
     * this lives exactly as long as the process does, which is the point.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Trim Coil's memory cache when the OS signals memory pressure, preventing the
    // app from holding onto image memory that the system urgently needs elsewhere.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val cache = SingletonImageLoader.get(this).memoryCache
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
                cache?.trimToSize((cache.maxSize * TRIM_MEMORY_UI_HIDDEN_FACTOR).toLong())
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                cache?.trimToSize(0L)
            }
        }
    }

    /**
     * Configures the global Coil [ImageLoader] singleton used throughout the app.
     *
     * - Memory cache: capped at 15% of the application's available memory class with
     *   a hard ceiling of 256 MB, preventing excessive heap use on large-RAM tablets.
     * - Disk cache: sized from the user's Settings preference
     *   ([GeneralPreferences.coilDiskCacheSizeMb]), falling back to
     *   [GeneralPreferences.DEFAULT_COIL_DISK_CACHE_MB] if the read fails.
     * - allowRgb565: opaque images (most manga pages) decode as RGB_565 (2 bytes/pixel)
     *   instead of ARGB_8888 (4 bytes/pixel), halving per-page memory cost.
     * - Networking: backed by the shared [OkHttpClient] for connection pooling and
     *   consistent headers (e.g. User-Agent, Referer) set by extension interceptors.
     */
    override fun newImageLoader(context: Context): ImageLoader {
        val maxMemoryCacheBytes = minOf(
            (Runtime.getRuntime().maxMemory() * 0.15).toLong(),
            256L * 1024 * 1024
        )
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(maxMemoryCacheBytes)
                    .build()
            }
            .diskCache {
                // This factory lambda is invoked lazily by Coil on first disk-cache access
                // (not synchronously here at ImageLoader construction time), so a blocking
                // DataStore read is safe — it never blocks app startup or the calling thread
                // that builds the ImageLoader. The read is pinned to Dispatchers.IO as
                // defense-in-depth in case a future Coil version invokes this on the main
                // thread. Falls back to the preference default if the read fails.
                val sizeMb = runCatching {
                    runBlocking(Dispatchers.IO) { generalPreferences.coilDiskCacheSizeMb.first() }
                }.getOrDefault(GeneralPreferences.DEFAULT_COIL_DISK_CACHE_MB)
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(sizeMb.toLong() * 1024 * 1024)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = {
                    // Tags image traffic for the Data Usage dashboard. The page-image headers
                    // themselves come from the injected client, which Downloader also uses, so
                    // display and offline saving behave identically.
                    pageImageOkHttpClient.newBuilder()
                        .addInterceptor { chain ->
                            chain.proceed(
                                chain.request().newBuilder()
                                    .tag(RequestCategory::class.java, RequestCategory.IMAGE_CACHE)
                                    .build()
                            )
                        }
                        .build()
                }))
            }
            .allowRgb565(true)
            .crossfade(300)
            .build()
    }
}
