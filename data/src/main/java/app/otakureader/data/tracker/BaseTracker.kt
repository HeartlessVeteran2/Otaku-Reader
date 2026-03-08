package app.otakureader.data.tracker

import app.otakureader.domain.model.TrackItem
import app.otakureader.domain.model.TrackService

/**
 * Common contract for all external tracking service clients.
 */
abstract class BaseTracker {
    /** The [TrackService] this tracker handles. */
    abstract val service: TrackService

    /**
     * Returns the OAuth authorization URL to open in a browser.
     * For Kitsu (password-grant) this method is not used – authenticate directly via [login].
     */
    abstract fun getAuthorizationUrl(): String

    /**
     * Exchanges an OAuth [authCode] (or access token for implicit-grant services) for a session.
     * Stores tokens internally via the preferences layer.
     */
    abstract suspend fun login(authCode: String)

    /** Logs the user out by clearing stored tokens. */
    abstract suspend fun logout()

    /** Returns true when a valid access token is stored. */
    abstract suspend fun isLoggedIn(): Boolean

    /**
     * Searches the service for entries matching [title].
     * Returns a list of candidate [TrackItem]s (id, title, remoteUrl populated).
     */
    abstract suspend fun searchManga(title: String): List<TrackItem>

    /**
     * Pushes progress stored in [track] to the remote service.
     * Reads [TrackItem.lastChapterRead], [TrackItem.status], [TrackItem.score].
     */
    abstract suspend fun update(track: TrackItem)
}
