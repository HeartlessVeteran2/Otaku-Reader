package app.otakureader.core.webview

import app.otakureader.core.network.cloudflare.CloudflareChallengeSolver
import app.otakureader.core.common.di.ApplicationScope
import app.otakureader.core.preferences.ChallengeUserAgentStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mediates Cloudflare challenges between the network layer and the app's navigation layer.
 *
 * Flow:
 * 1. [app.otakureader.core.network.cloudflare.CloudflareInterceptor] sees a challenge response
 *    and calls [solve], which suspends.
 * 2. A [ChallengeRequest] is added to [pendingChallenges].
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

    /**
     * [id] exists so cleanup can remove exactly its own entry.
     *
     * Without it, the two orderings of "drop the in-flight deferred" and "drop the pending
     * entry" are each wrong in a different way: clearing last lets a re-challenge slot in
     * between and have its brand-new pending entry deleted by the old challenge's cleanup, so
     * no WebView ever opens for it; clearing first leaves the stale deferred momentarily
     * reachable, so the re-challenge joins an already-completed one and gets a stale answer.
     * Matching on identity removes the choice — the same reason [inFlight] uses
     * `remove(key, value)`.
     */
    data class ChallengeRequest(val id: Long, val host: String, val url: String)

    private val _pendingChallenges = MutableStateFlow<List<ChallengeRequest>>(emptyList())

    /**
     * Hosts currently awaiting a challenge. Observe in the navigation host.
     *
     * **State, not an event stream.** A `SharedFlow` emission is dropped outright when nothing is
     * subscribed, so a challenge raised while the navigation layer was being torn down or
     * recreated — a rotation, a process-death restore — would vanish, and the blocked request
     * would sit waiting for its full timeout with no WebView ever appearing. Holding the pending
     * set as state means a collector that arrives late still sees the work.
     *
     * A list rather than a single value because two hosts can be blocked at once; the navigation
     * layer shows them one at a time.
     */
    val pendingChallenges: StateFlow<List<ChallengeRequest>> = _pendingChallenges.asStateFlow()

    /** In-flight challenges, one per host. See [solve] for why this exists. */
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val nextChallengeId = AtomicLong(0)

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

        val request = ChallengeRequest(nextChallengeId.incrementAndGet(), host, url)
        return try {
            _pendingChallenges.update { pending ->
                if (pending.any { it.host == host }) pending else pending + request
            }
            fresh.await()
        } catch (e: CancellationException) {
            // Completing before rethrowing is what keeps the joiners alive. They await this
            // deferred directly, so once it leaves the map below, nothing else can ever
            // complete it — and every other page of the chapter would hang until its own
            // timeout. The leader's cancellation must not become everyone's.
            fresh.complete(false)
            throw e
        } finally {
            // Both removals match on identity, so neither can touch a challenge that replaced
            // ours — which makes the order between them irrelevant rather than merely chosen.
            inFlight.remove(host, fresh)
            clearPending(request.id)
        }
    }

    override fun solvedUserAgent(host: String): String? = userAgentStore.userAgentFor(host)

    private fun String.containsClearanceCookie(): Boolean =
        split(';').any { it.substringBefore('=').trim() == CLEARANCE_COOKIE }

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
        val cleared = cookieString.orEmpty().containsClearanceCookie()
        if (cleared && !userAgent.isNullOrBlank()) {
            scope.launch { userAgentStore.store(host, userAgent) }
        }
        // Completing the deferred is the whole job. Removing the pending entry is deliberately
        // NOT done here: [solve]'s own cleanup does it, by id, the instant its await returns.
        //
        // A host-wide removal here would race a re-challenge — the old solve can finish and a
        // new one add its entry in the window between completing the deferred and clearing, and
        // the host-wide sweep would delete the newcomer, leaving its caller blocked with no
        // WebView. Giving the pending set exactly one owner removes the race rather than
        // narrowing it; this method had kept a second, host-wide path after solve's was made
        // identity-matched.
        inFlight[host]?.complete(cleared)
    }

    private fun clearPending(id: Long) {
        _pendingChallenges.update { pending -> pending.filterNot { it.id == id } }
    }

    private companion object {
        /**
         * The cookie Cloudflare issues on passing a challenge.
         *
         * Success has to be judged on *this* cookie, not on the cookie header being non-empty.
         * The WebView reports every cookie the site has set, so a session or consent cookie from
         * merely loading the page would read as clearance — the request would be released, the
         * User-Agent stored as if it had earned something, and the single retry would fail
         * against a challenge the user never actually passed.
         */
        const val CLEARANCE_COOKIE = "cf_clearance"
    }
}
