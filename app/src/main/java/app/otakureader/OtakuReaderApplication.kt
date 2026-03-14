package app.otakureader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.otakureader.core.discord.DiscordRpcService
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.shortcut.AppShortcutManager
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var appShortcutManager: AppShortcutManager

    @Inject
    lateinit var discordRpcService: DiscordRpcService

    @Inject
    lateinit var generalPreferences: GeneralPreferences

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // Initialize Discord RPC if enabled
        initializeDiscordRpc()
    }

    private fun initializeDiscordRpc() {
        applicationScope.launch {
            try {
                if (generalPreferences.discordRpcEnabled.first()) {
                    discordRpcService.initialize()
                }
            } catch (_: Exception) {
                // Fail silently – Discord RPC is optional
            }
        }
    }
}
