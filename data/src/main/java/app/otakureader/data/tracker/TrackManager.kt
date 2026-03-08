package app.otakureader.data.tracker

import app.otakureader.domain.model.TrackItem
import app.otakureader.domain.model.TrackService
import app.otakureader.domain.repository.TrackRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager that coordinates all external tracking services.
 *
 * It holds references to each [BaseTracker] and delegates per-service
 * operations to the appropriate implementation, while [TrackRepository] handles
 * persistence of the user's tracked entries.
 */
@Singleton
class TrackManager @Inject constructor(
    private val trackers: Set<@JvmSuppressWildcards BaseTracker>,
    private val trackRepository: TrackRepository
) {
    /** Returns the [BaseTracker] for the given [service], or null if not registered. */
    fun getTracker(service: TrackService): BaseTracker? =
        trackers.find { it.service == service }

    /** Returns all registered trackers. */
    fun getTrackers(): List<BaseTracker> = trackers.toList()

    /**
     * Returns the authorization URL for [service].
     * The caller is responsible for opening the URL in a browser.
     */
    fun getAuthorizationUrl(service: TrackService): String =
        getTracker(service)?.getAuthorizationUrl() ?: ""

    /**
     * Completes login for [service] using the [authCode] received from the OAuth redirect.
     */
    suspend fun login(service: TrackService, authCode: String) {
        getTracker(service)?.login(authCode)
    }

    /** Logs out of [service] and clears stored tokens. */
    suspend fun logout(service: TrackService) {
        getTracker(service)?.logout()
    }

    /** Returns true if the user is currently logged in to [service]. */
    suspend fun isLoggedIn(service: TrackService): Boolean =
        getTracker(service)?.isLoggedIn() ?: false

    /**
     * Searches [service] for manga matching [title].
     * Returns an empty list if not logged in.
     */
    suspend fun searchManga(service: TrackService, title: String): List<TrackItem> =
        getTracker(service)?.searchManga(title) ?: emptyList()

    /**
     * Updates the remote tracking entry for [track] and persists the updated state locally.
     * No-ops silently if the service tracker is not found or the user is not logged in.
     */
    suspend fun updateTracking(track: TrackItem) {
        val tracker = getTracker(track.service) ?: return
        if (!tracker.isLoggedIn()) return
        tracker.update(track)
        trackRepository.upsertTrack(track)
    }

    /**
     * Syncs reading progress for a completed chapter.
     *
     * Called by the reader when a chapter is finished.  Looks up every tracked
     * entry for [mangaId] and updates the remote service if [chapterNumber]
     * is greater than the previously stored [TrackItem.lastChapterRead].
     */
    suspend fun onChapterRead(mangaId: Long, chapterNumber: Float) {
        val tracks = trackRepository.getTracksSnapshot(mangaId)
        for (track in tracks) {
            if (chapterNumber > track.lastChapterRead) {
                val updated = track.copy(lastChapterRead = chapterNumber)
                updateTracking(updated)
            }
        }
    }
}
