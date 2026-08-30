package app.otakureader.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stamps the app's User-Agent on requests that do not already carry one.
 *
 * Without it OkHttp's own `BridgeInterceptor` fills in `okhttp/4.12.0`, which a fair number of
 * sites reject outright — and which is not something the user can change.
 *
 * **Only when absent**, unlike [app.otakureader.core.network.cloudflare.ChallengeUserAgentInterceptor],
 * which overwrites. The distinction is which one the caller is entitled to decide. A source that
 * sets its own User-Agent has usually done so because the site requires that exact string, so
 * replacing it would break the source in the name of a preference. A cleared Cloudflare host is the
 * opposite case: the clearance cookie is bound to the solving identity, so anything else is
 * rejected no matter who chose it.
 *
 * APK extensions do not come through here for their own requests — they set a User-Agent from
 * `NetworkHelper.defaultUserAgentProvider()`, which reads the same `NetworkSettings` value, so the
 * override reaches them by that route instead.
 *
 * Takes a function rather than `NetworkSettings` itself: it needs one string, and evaluating it per
 * call is the property that makes the setting apply without a restart. Naming that in the type
 * keeps a later "cache this" refactor from quietly undoing it.
 */
class UserAgentInterceptor(
    private val userAgent: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER) != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER, userAgent()).build())
    }

    private companion object {
        const val HEADER = "User-Agent"
    }
}
