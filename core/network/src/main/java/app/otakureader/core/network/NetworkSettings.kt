package app.otakureader.core.network

import app.otakureader.core.common.di.ApplicationScope
import app.otakureader.core.preferences.DohProvider
import app.otakureader.core.preferences.NetworkPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The current value of each Advanced network setting, readable without suspending.
 *
 * ## Why a holder rather than reading DataStore
 *
 * The shared `OkHttpClient` is a singleton built once, so anything baked into it at construction
 * would need an app restart to change — and a network setting that silently does nothing until you
 * relaunch is worse than no setting. The pieces that consume these values are an `Interceptor` and
 * a `Dns`, both of which are called synchronously on OkHttp's threads and cannot suspend on a disk
 * read. So the client is built once against *this*, and this tracks the preferences.
 *
 * ## Why the pre-first-emission window is safe
 *
 * Each field starts at the value the app used before the setting existed: the built-in User-Agent,
 * the system resolver, no logging. A request issued in the moment between construction and the
 * first DataStore emission therefore behaves exactly as it did before any of this — not as an
 * error state, and not as some arbitrary default. It corrects itself as soon as the flow emits.
 *
 * These are collected continuously rather than read once, because the settings screen writes them
 * while the app is running and the point is that the change takes effect immediately.
 */
@Singleton
class NetworkSettings @Inject constructor(
    preferences: NetworkPreferences,
    @ApplicationScope scope: CoroutineScope,
) {

    /** The User-Agent to send when a caller has not chosen one. Never blank. */
    @Volatile
    var userAgent: String = DEFAULT_USER_AGENT
        private set

    @Volatile
    var dohProvider: DohProvider = DohProvider.OFF
        private set

    @Volatile
    var verboseLogging: Boolean = false
        private set

    init {
        scope.launch {
            preferences.userAgent.collect { userAgent = it.ifBlank { DEFAULT_USER_AGENT } }
        }
        scope.launch { preferences.dohProvider.collect { dohProvider = it } }
        scope.launch { preferences.verboseLogging.collect { verboseLogging = it } }
    }

    companion object {
        /**
         * The identity the app presents when nothing else has chosen one.
         *
         * Canonical here rather than in `NetworkHelper`, which used to own the only copy: that
         * class belongs to the APK backend, and the JavaScript backend, the page-image client and
         * the update checker all need the same string. `NetworkHelper` now reads from here.
         */
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
