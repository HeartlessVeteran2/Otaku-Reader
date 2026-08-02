package app.otakureader.core.webview

import app.otakureader.core.network.cloudflare.CloudflareChallengeSolver
import app.otakureader.core.common.di.ApplicationScope
import app.otakureader.core.preferences.ChallengeUserAgentStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mediates Cloudflare challenges between the network layer and the app's navigation layer.
 *
 * Flow:
 * 1. [app.otakureader.core.network.cloudflare.CloudflareInterceptor] sees a challenge response
 *    and calls [solve], which suspends.
 * 2. A [ChallengeRequest] is emitted on [pendingChallenge].
 * 3. The navigation host observes it and opens the WebView.
 * 4. On close, the nav layer calls [completeChallenge] with the cookies and the WebView's
 *    User-Agent.
 * 5. [solve] resumes; the interceptor retries the original request.
 *
 * Keyed by **host**, not by source. Cloudflare clearance is granted per domain, so two sources
 * on the same host share one challenge — and, more importantly, the interceptor only ever knows
 * a URL. An earlier version keyed on a source id that nothing in the network layer could supply,
 * which is part of why none of this was ever wired up.
 */
@Singleton
class WebViewChallengeManager @Inject constructor(
    private val userAgentStore: ChallengeUserAgentStore,
    @param:ApplicationScope private val scope: CoroutineScope,
) : CloudflareChallengeSolver {

    data class ChallengeRequest(val host: String, val url: String)

    private val _pendingChallenge = MutableSharedFlow<ChallengeRequest>(extraBufferCapacity = 8)

    /** Emits whenever a host needs a challenge solved. Observe in the navigation host. */
    val pendingChallenge: SharedFlow<ChallengeRequest> = _pendingChallenge.asSharedFlow()

    /** In-flight challenges, one per host. See [solve] for why this exists. */
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /**
     * Suspend until [host]'s challenge is solved or abandoned.
     *
     * **Coalesced by host**, which is not an optimisation but a usability requirement: a blocked
     * chapter fires one request per page, so twenty callers arrive here within milliseconds of
     * each other. Without coalescing the user would face twenty stacked WebViews for one
     * challenge. `putIfAbsent` decides the winner atomically — a check-then-put would let two
     * callers both believe they were first.
     */
    override suspend fun solve(host: String, url: String): Boolean {
        val fresh = CompletableDeferred<Boolean>()
        val existing = inFlight.putIfAbsent(host, fresh)
        if (existing != null) return existing.await()

        return try {
            _pendingChallenge.emit(ChallengeRequest(host, url))
            fresh.await()
        } catch (e: CancellationException) {
            // Completing before rethrowing is what keeps the joiners alive. They await this
            // deferred directly, so once it leaves the map below, nothing else can ever
            // complete it — and every other page of the chapter would hang until its own
            // timeout. The leader's cancellation must not become everyone's.
            fresh.complete(false)
            throw e
        } finally {
            // remove(key, value) so a challenge started after ours is never removed by us.
            inFlight.remove(host, fresh)
        }
    }

    override fun solvedUserAgent(host: String): String? = userAgentStore.userAgentFor(host)

    /**
     * Signal that the WebView for [host] has closed.
     *
     * Cookies need no transfer: the app's cookie jar is backed by the same Android
     * `CookieManager` the WebView writes to, so clearance is already visible to OkHttp.
     *
     * The [userAgent] is the part that would otherwise be lost, and losing it makes the whole
     * flow useless — the cookie is bound to it, so a request under any other identity is
     * challenged again. It is stored only alongside a cookie that actually arrived, so a stored
     * User-Agent always corresponds to clearance that was really obtained.
     */
    fun completeChallenge(host: String, cookieString: String?, userAgent: String?) {
        val cleared = !cookieString.isNullOrEmpty()
        if (cleared && !userAgent.isNullOrBlank()) {
            scope.launch { userAgentStore.store(host, userAgent) }
        }
        inFlight[host]?.complete(cleared)
    }
}
