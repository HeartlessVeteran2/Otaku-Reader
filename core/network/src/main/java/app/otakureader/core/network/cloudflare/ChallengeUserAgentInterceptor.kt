package app.otakureader.core.network.cloudflare

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends the User-Agent that solved a host's Cloudflare challenge, for requests to that host.
 *
 * Cloudflare binds clearance to the User-Agent that earned it. Replaying the cookie under a
 * different one is treated as a stolen cookie and challenged again — so without this, solving a
 * challenge appears to work and then the very next request is blocked exactly as before. The
 * user is left tapping through a WebView repeatedly with nothing improving, which looks like the
 * bypass being broken rather than a mismatched identity.
 *
 * The mismatch is guaranteed here rather than hypothetical: `NetworkHelper` sends a fixed
 * `DEFAULT_USER_AGENT` for extension traffic, while the WebView reports the system WebView's own
 * string. They are never the same.
 *
 * **Overrides an existing header**, unlike the page-image header injection, and deliberately so.
 * Extensions set their own User-Agent, but for a challenged host the cookie only works with the
 * solving one — deferring to the extension's preference would simply keep it blocked.
 */
@Singleton
class ChallengeUserAgentInterceptor @Inject constructor(
    private val solver: CloudflareChallengeSolver,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Null for every host that has never been challenged, which is nearly all of them —
        // tracker APIs and unprotected sources keep whatever identity they chose.
        val userAgent = solver.solvedUserAgent(request.url.host)
            ?: return chain.proceed(request)

        return chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
    }
}
