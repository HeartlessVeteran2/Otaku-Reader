package app.otakureader.core.network.cloudflare

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Response
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
         * Generous enough for a real CAPTCHA, short enough that a forgotten screen recovers.
         */
        const val SOLVE_TIMEOUT_MS = 3 * 60 * 1000L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isCloudflareChallenge()) return response

        val url = response.request.url
        // The body is never read, so it must be closed explicitly or the connection leaks while
        // the user is off solving the challenge — potentially for minutes.
        response.close()

        // Blocking is correct: this call *is* the thing waiting on the user, and the timeout
        // above bounds it. Solving is coalesced by host in the implementation, so the twenty
        // parallel image requests behind a blocked page produce one WebView, not twenty.
        val solved = runBlocking {
            withTimeoutOrNull(SOLVE_TIMEOUT_MS) {
                solver.solve(url.host, url.toString())
            } ?: false
        }

        if (!solved) {
            // Re-issue rather than fabricate a response, so the caller sees the site's real
            // answer — an extension parsing an error page is easier to diagnose than one handed
            // a synthetic failure that never came from the server.
            return chain.proceed(chain.request())
        }

        // Retry once only. If clearance did not take, retrying again just re-opens the WebView
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
