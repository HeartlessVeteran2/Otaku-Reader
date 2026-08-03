package app.otakureader.core.network.cloudflare

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects a Cloudflare browser challenge and gives the user a way through it.
 *
 * Without this, a Cloudflare-protected source simply fails. The request comes back 403, the
 * extension reports an error, and there is nothing the user can do about it — no prompt, no
 * WebView, no explanation. Since a large share of manga hosts sit behind Cloudflare, that is a
 * broad and completely silent class of "this source doesn't work".
 *
 * On detection this suspends the call, asks for the challenge to be solved, and — if it was —
 * retries once. The retry carries the clearance cookie automatically, because the cookie jar and
 * the WebView share Android's `CookieManager`.
 */
@Singleton
class CloudflareInterceptor @Inject constructor(
    private val solver: CloudflareChallengeSolver,
) : Interceptor {

    private companion object {
        /**
         * Ceiling on how long a blocked call waits for the user.
         *
         * This blocks an OkHttp dispatcher thread, so it cannot wait indefinitely: a handful of
         * abandoned challenges would otherwise consume the pool and stall unrelated requests.
         *
         * **Must stay below the client's `callTimeout`, currently 2 minutes** (see
         * `NetworkModule.provideOkHttpClient`). This is an application interceptor, so it runs
         * *inside* the call, and `callTimeout` spans the original request, this wait, and the
         * retry together. A budget above it is not merely unreachable — the call would be
         * cancelled mid-challenge and the user's work thrown away just as they finished.
         *
         * 100 seconds leaves room for the retry to complete afterwards. Raising the client's
         * `callTimeout` instead was the alternative and is worse: it applies to every request
         * the app makes, so a stalled page download would hang proportionally longer for the
         * sake of a path most requests never take.
         */
        const val SOLVE_TIMEOUT_MS = 100 * 1000L

        /**
         * Cap on the challenge page kept in memory while the user solves it.
         *
         * A Cloudflare interstitial is a few KB of HTML. This exists only so the response can be
         * handed back intact if the user gives up, without holding the connection open for the
         * length of the challenge.
         */
        const val MAX_CHALLENGE_BODY_BYTES = 256L * 1024

        /** Stand-in when the challenge body could not be buffered; the status code still carries. */
        val EMPTY_BODY: ResponseBody = ByteArray(0).toResponseBody(null)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isCloudflareChallenge()) return response

        val url = response.request.url

        // Buffer the challenge page, then release the connection before waiting on the user.
        //
        // Both halves matter. Holding the response open would tie up a connection for as long
        // as the challenge takes, which can be minutes. Closing it without buffering would
        // leave nothing to hand back if the user gives up — which is how an earlier version
        // ended up re-issuing the request on failure, doubling the traffic and risking a
        // repeated side effect for a POST. A challenge page is a few KB of HTML, so keeping a
        // bounded copy costs nothing.
        // Peek one byte past the cap so an oversized page is *detectable* rather than silently
        // truncated. A caller handed half a challenge page would parse the fragment as if it
        // were the whole document — a wrong result that looks successful, which is worse than an
        // empty body it can recognise as a failure.
        val buffered = runCatching {
            response.peekBody(MAX_CHALLENGE_BODY_BYTES + 1)
                .takeIf { it.contentLength() <= MAX_CHALLENGE_BODY_BYTES }
        }.getOrNull()
        response.close()

        // Blocking is correct: this call *is* the thing waiting on the user, and the timeout
        // bounds it. Solving is coalesced by host in the implementation, so the twenty parallel
        // image requests behind a blocked page produce one WebView, not twenty.
        val solved = runBlocking {
            withTimeoutOrNull(SOLVE_TIMEOUT_MS) {
                solver.solve(url.host, url.toString())
            } ?: false
        }

        if (!solved) {
            // Hand back the site's own answer rather than repeating the request. The caller
            // sees the real challenge page and the real status code, which is both honest and
            // cheaper — and for a non-idempotent request it is the difference between one
            // POST and two.
            return response.newBuilder()
                .body(buffered ?: EMPTY_BODY)
                .build()
        }

        // Retry once only. If clearance did not take, retrying again would re-open the WebView
        // in a loop the user cannot escape.
        return chain.proceed(chain.request())
    }
}

/**
 * A challenge is a specific pairing of status code and `Server` header.
 *
 * Both are required, and that is the whole accuracy of the detector. The code alone catches
 * every ordinary 403 on the internet and would pop a WebView at each one; the header alone
 * catches every *successful* response from a Cloudflare-fronted site, which is most of them.
 *
 * A file-scope function rather than a method, so it can be tested against a constructed
 * [Response] without building an interceptor chain.
 */
internal fun Response.isCloudflareChallenge(): Boolean =
    code in CloudflareChallenge.CODES &&
        header("Server")?.lowercase() in CloudflareChallenge.SERVERS

internal object CloudflareChallenge {
    /** 503 is the classic interstitial; 403 covers managed-challenge and block responses. */
    val CODES = setOf(403, 503)
    val SERVERS = setOf("cloudflare", "cloudflare-nginx")
}
