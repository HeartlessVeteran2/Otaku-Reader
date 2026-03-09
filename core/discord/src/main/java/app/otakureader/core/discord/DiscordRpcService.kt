package app.otakureader.core.discord

import android.content.Context
import app.otakureader.core.discord.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jensklingenberg.kizzyrpc.KizzyRPC
import de.jensklingenberg.kizzyrpc.entities.Activity
import de.jensklingenberg.kizzyrpc.entities.Timestamps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing Discord Rich Presence integration.
 * Uses Kizzy library to communicate with Discord.
 */
@Singleton
class DiscordRpcService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var kizzyRpc: KizzyRPC? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private var currentActivity: ReadingActivity? = null
    private var sessionStartTime: Long = System.currentTimeMillis()

    /** Activity queued while the connection is being established. Applied once connected. */
    @Volatile private var pendingActivity: Activity? = null

    /**
     * Initialize the Discord RPC connection.
     * Should be called when the app starts if Discord RPC is enabled.
     */
    fun initialize() {
        // Guard against concurrent initialize() calls; only one connection is created.
        synchronized(this) {
            if (kizzyRpc != null) return
        }

        scope.launch {
            try {
                val rpc = KizzyRPC(
                    appId = BuildConfig.DISCORD_APPLICATION_ID,
                    status = "online"
                )
                // Atomically store the connection and flush any queued activity.
                val pending: Activity?
                synchronized(this@DiscordRpcService) {
                    if (kizzyRpc != null) {
                        // Another coroutine beat us here; close the duplicate.
                        rpc.closeRPC()
                        return@launch
                    }
                    kizzyRpc = rpc
                    pending = pendingActivity
                    pendingActivity = null
                }
                _connectionState.value = ConnectionState.Connected
                pending?.let { rpc.updatePresence(it) }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Disconnect from Discord.
     */
    fun disconnect() {
        scope.launch {
            try {
                kizzyRpc?.closeRPC()
                kizzyRpc = null
                _connectionState.value = ConnectionState.Disconnected
                currentActivity = null
            } catch (e: Exception) {
                // Ignore disconnect errors
            }
        }
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

        val discordActivity = buildActivity(activity)
        scope.launch {
            val rpc = kizzyRpc
            if (rpc != null) {
                try {
                    rpc.updatePresence(discordActivity)
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Error(e.message ?: "Failed to update presence")
                }
            } else {
                pendingActivity = discordActivity
            }
        }
    }

    /**
     * Clear the reading presence and show browsing status or disconnect.
     */
    fun clearReadingPresence(showBrowsing: Boolean = true) {
        if (showBrowsing) {
            updateBrowsingPresence()
        } else {
            val idleActivity = Activity(
                details = "Idle",
                state = "Not reading",
                largeImage = DISCORD_LOGO_IMAGE,
                largeText = APP_NAME
            )
            scope.launch {
                val rpc = kizzyRpc
                if (rpc != null) {
                    try {
                        rpc.updatePresence(idleActivity)
                    } catch (e: Exception) {
                        // Ignore errors when clearing presence
                    }
                } else {
                    pendingActivity = idleActivity
                }
            }
        }
    }

    /**
     * Update presence to show browsing status.
     */
    fun updateBrowsingPresence() {
        val activity = Activity(
            details = "Browsing library",
            state = "Looking for manga",
            largeImage = DISCORD_LOGO_IMAGE,
            largeText = APP_NAME,
            timestamps = Timestamps(start = sessionStartTime)
        )
        scope.launch {
            val rpc = kizzyRpc
            if (rpc != null) {
                try {
                    rpc.updatePresence(activity)
                } catch (e: Exception) {
                    // Ignore errors
                }
            } else {
                pendingActivity = activity
            }
        }
    }

    /**
     * Reset the session timer. Call when starting to read a new manga/chapter.
     */
    fun resetSessionTimer() {
        sessionStartTime = System.currentTimeMillis()
    }

    private fun buildActivity(readingActivity: ReadingActivity): Activity {
        val statusText = when (readingActivity.status) {
            ReadingStatus.READING -> "Reading"
            ReadingStatus.PAUSED -> "Paused"
            ReadingStatus.BROWSING -> "Browsing"
        }

        val stateText = buildString {
            append(readingActivity.chapterName)
            if (readingActivity.page != null && readingActivity.totalPages != null) {
                append(" • Page ${readingActivity.page}/${readingActivity.totalPages}")
            }
        }

        return Activity(
            details = "${statusText}: ${readingActivity.mangaTitle}",
            state = stateText,
            largeImage = DISCORD_LOGO_IMAGE,
            largeText = APP_NAME,
            smallImage = when (readingActivity.status) {
                ReadingStatus.READING -> DISCORD_READING_IMAGE
                ReadingStatus.PAUSED -> DISCORD_PAUSED_IMAGE
                ReadingStatus.BROWSING -> DISCORD_BROWSING_IMAGE
            },
            smallText = statusText,
            timestamps = if (readingActivity.status == ReadingStatus.READING) {
                Timestamps(start = readingActivity.startTime)
            } else {
                null
            }
        )
    }

    /**
     * Check if the service is connected to Discord.
     */
    fun isConnected(): Boolean = kizzyRpc != null && _connectionState.value == ConnectionState.Connected

    companion object {
        private const val APP_NAME = "Otaku Reader"

        // Asset keys for Discord images (these would be uploaded to Discord Developer Portal)
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
    data object Connected : ConnectionState()
    data object Disconnected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
