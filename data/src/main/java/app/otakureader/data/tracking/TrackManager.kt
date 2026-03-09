package app.otakureader.data.tracking

import app.otakureader.domain.tracking.Tracker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry for all enabled tracker services.
 *
 * Inject this singleton wherever tracker access is needed, rather than
 * injecting individual trackers directly.
 */
@Singleton
class TrackManager @Inject constructor(
    trackers: Set<@JvmSuppressWildcards Tracker>
) {
    private val registry: Map<Int, Tracker> = trackers.associateBy { it.id }

    /** All registered trackers. */
    val all: List<Tracker> get() = registry.values.toList()

    /** Returns the tracker with the given [id], or `null` if not registered. */
    fun get(id: Int): Tracker? = registry[id]

    /** Returns only the trackers that the user has authenticated with. */
    val loggedIn: List<Tracker>
        get() = all.filter { it.isLoggedIn }
}
