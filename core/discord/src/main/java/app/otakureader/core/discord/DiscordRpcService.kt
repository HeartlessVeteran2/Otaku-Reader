package app.otakureader.core.discord

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.otakureader.core.discord.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing Discord Rich Presence integration.
 *
 * This service is currently responsible for validating Discord
 * availability and tracking Rich Presence activity state locally.
 * It does not perform any network or IPC communication yet; a
 * concrete Discord RPC transport (e.g., WebSocket/Gateway or
 * official mobile SDK) will be integrated separately. When
 * Discord is not installed or not running, the service falls
 * back gracefully by remaining in a disconnected or error state.
 */
@Singleton
class DiscordRpcService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private var currentActivity: ReadingActivity? = null
    private var sessionStartTime: Long = System.currentTimeMillis()

    /**
     * Initialize the Discord RPC service.
     * Should be called when the app starts if Discord RPC is enabled.
     *
     * Note: The service currently validates that Discord is installed and stores
     * activity state locally. Actual presence updates will be sent once a
     * Discord mobile RPC transport (e.g., Gateway WebSocket or official SDK)
     * is integrated.
     */
    fun initialize() {
        if (BuildConfig.DISCORD_APPLICATION_ID.isBlank()) return
        if (!isDiscordInstalled()) {
            _connectionState.value = ConnectionState.Error("Discord is not installed")
            return
        }
        _connectionState.value = ConnectionState.Initialized
    }

    /**
     * Disconnect from Discord.
     */
    fun disconnect() {
        currentActivity = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Update the Discord Rich Presence with current reading activity.
     *
     * @param mangaTitle The title of the manga being read
     * @param chapterName The name of the current chapter
     * @param status The reading status (reading, paused, browsing)
     * @param page Current page number (optional)
     * @param totalPages Total pages in chapter (optional)
     */
    fun updateReadingPresence(
        mangaTitle: String,
        chapterName: String,
        status: ReadingStatus,
        page: Int? = null,
        totalPages: Int? = null
    ) {
        val activity = ReadingActivity(
            mangaTitle = mangaTitle,
            chapterName = chapterName,
            status = status,
            page = page,
            totalPages = totalPages,
            startTime = sessionStartTime
        )
        currentActivity = activity

        scope.launch {
            try {
                sendPresenceUpdate(activity)
            } catch (_: Exception) {
                // Fail silently – Discord RPC is best-effort
            }
        }
    }

    /**
     * Clear the reading presence and show browsing status or disconnect.
     */
    fun clearReadingPresence(showBrowsing: Boolean = true) {
        currentActivity = null
        if (showBrowsing) {
            updateBrowsingPresence()
        } else {
            disconnect()
        }
    }

    /**
     * Update presence to show browsing status.
     */
    fun updateBrowsingPresence() {
        val activity = ReadingActivity(
            mangaTitle = "",
            chapterName = "",
            status = ReadingStatus.BROWSING,
            startTime = sessionStartTime
        )
        scope.launch {
            try {
                sendPresenceUpdate(activity)
            } catch (_: Exception) {
                // Fail silently
            }
        }
    }

    /**
     * Reset the session timer. Call when starting to read a new manga/chapter.
     */
    fun resetSessionTimer() {
        sessionStartTime = System.currentTimeMillis()
    }

    /**
     * Check if the service is initialized and ready to track activity.
     */
    fun isConnected(): Boolean = _connectionState.value == ConnectionState.Initialized

    /**
     * Build the JSON payload for a Discord activity update.
     * This follows the Discord Rich Presence activity format.
     */
    private fun buildActivityPayload(activity: ReadingActivity): JSONObject {
        val statusText = when (activity.status) {
            ReadingStatus.READING -> "Reading"
            ReadingStatus.PAUSED -> "Paused"
            ReadingStatus.BROWSING -> "Browsing"
        }

        val details = if (activity.status == ReadingStatus.BROWSING) {
            "Browsing library"
        } else {
            "${statusText}: ${activity.mangaTitle}"
        }

        val state = if (activity.status == ReadingStatus.BROWSING) {
            "Looking for manga"
        } else {
            buildString {
                append(activity.chapterName)
                if (activity.page != null && activity.totalPages != null) {
                    append(" • Page ${activity.page}/${activity.totalPages}")
                }
            }
        }

        return JSONObject().apply {
            put("details", details)
            put("state", state)
            put("assets", JSONObject().apply {
                put("large_image", DISCORD_LOGO_IMAGE)
                put("large_text", APP_NAME)
                if (activity.status != ReadingStatus.BROWSING) {
                    put("small_image", when (activity.status) {
                        ReadingStatus.READING -> DISCORD_READING_IMAGE
                        ReadingStatus.PAUSED -> DISCORD_PAUSED_IMAGE
                        ReadingStatus.BROWSING -> DISCORD_BROWSING_IMAGE
                    })
                    put("small_text", statusText)
                }
            })
            if (activity.status == ReadingStatus.READING) {
                put("timestamps", JSONObject().apply {
                    put("start", activity.startTime)
                })
            }
        }
    }

    /**
     * Send a presence update. Currently stores the activity state locally and
     * validates the payload format. The actual Discord IPC transport will be
     * connected here once Discord's mobile RPC SDK or a WebSocket-based
     * Gateway bridge is integrated.
     */
    private fun sendPresenceUpdate(activity: ReadingActivity) {
        if (BuildConfig.DISCORD_APPLICATION_ID.isBlank()) return
        if (_connectionState.value != ConnectionState.Initialized) return

        // Build the payload (validates format and keeps it ready for future transport)
        buildActivityPayload(activity)
    }

    /**
     * Check if Discord is installed on the device.
     */
    private fun isDiscordInstalled(): Boolean {
        val discordPackages = listOf(
            "com.discord",           // Discord stable
            "com.discord.canary",    // Discord Canary
            "com.discord.ptb"        // Discord PTB
        )
        return discordPackages.any { pkg ->
            try {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(pkg, 0)
                }
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    companion object {
        private const val APP_NAME = "Otaku Reader"

        // Asset keys for Discord images (upload to Discord Developer Portal)
        private const val DISCORD_LOGO_IMAGE = "app_logo"
        private const val DISCORD_READING_IMAGE = "reading"
        private const val DISCORD_PAUSED_IMAGE = "paused"
        private const val DISCORD_BROWSING_IMAGE = "browsing"
    }
}

/**
 * Represents the current reading activity for Discord Rich Presence.
 */
data class ReadingActivity(
    val mangaTitle: String,
    val chapterName: String,
    val status: ReadingStatus,
    val page: Int? = null,
    val totalPages: Int? = null,
    val startTime: Long = System.currentTimeMillis()
)

/**
 * Reading status states.
 */
enum class ReadingStatus {
    READING,
    PAUSED,
    BROWSING
}

/**
 * Connection states for Discord RPC.
 */
sealed class ConnectionState {
    /** Service is initialized and tracking activity (transport pending). */
    data object Initialized : ConnectionState()
    data object Disconnected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}