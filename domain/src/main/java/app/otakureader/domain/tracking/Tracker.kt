package app.otakureader.domain.tracking

import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus

/**
 * Common contract that every tracker service must fulfil.
 */
interface Tracker {
    /** Unique identifier matching [app.otakureader.domain.model.TrackerType]. */
    val id: Int

    /** Human-readable display name shown in the UI. */
    val name: String

    /** Whether the user is currently authenticated with this service. */
    val isLoggedIn: Boolean

    /**
     * Authenticate the user. For OAuth-based services this opens the OAuth
     * flow; for credential-based services this performs a direct login.
     *
     * @return `true` on success.
     */
    suspend fun login(username: String, password: String): Boolean

    /** Clear any stored credentials / tokens. */
    fun logout()

    /**
     * Search the remote service for manga matching [query].
     *
     * @return A list of partial [TrackEntry] objects (remoteId + title populated).
     */
    suspend fun search(query: String): List<TrackEntry>

    /**
     * Fetch the current tracking entry for the given [remoteId].
     */
    suspend fun find(remoteId: Long): TrackEntry?

    /**
     * Create or update a tracking entry on the remote service.
     *
     * Implementations must throw an exception on remote failure rather than
     * silently returning the input entry, so callers can distinguish success
     * from failure and avoid persisting stale data locally.
     */
    suspend fun update(entry: TrackEntry): TrackEntry

    /**
     * Map a remote service status value to the internal [TrackStatus] enum.
     */
    fun toTrackStatus(remoteStatus: Int): TrackStatus

    /**
     * Map the internal [TrackStatus] enum to the value expected by the remote service.
     */
    fun toRemoteStatus(status: TrackStatus): Int

    /**
     * Build the full OAuth authorization URL for this tracker.
     *
     * Only meaningful for OAuth-based trackers (MAL, AniList, Shikimori).
     * Credential-based trackers (Kitsu, MangaUpdates) should return `null`.
     *
     * The URL should include all required query parameters:
     * - client_id
     * - redirect_uri
     * - response_type (typically "code")
     * - state (CSRF protection)
     * - code_challenge / code_challenge_method (PKCE, if supported)
     *
     * @param codeVerifier The PKCE code verifier to derive code_challenge from
     * @return Fully-parameterized authorization URL, or `null` if not applicable
     */
    fun authorizationUrl(codeVerifier: String): String? = null
}
