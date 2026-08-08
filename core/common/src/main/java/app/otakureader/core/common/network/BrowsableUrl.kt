package app.otakureader.core.common.network

import java.net.URI
import java.net.URISyntaxException

/**
 * Whether a URL is one this app is willing to hand to a browser.
 *
 * ### Why this lives here rather than beside either caller
 *
 * It is checked in two places on purpose — once where third-party URLs are cached, deciding what
 * is worth storing, and again where a URL becomes an `Intent`, which is the check that cannot be
 * bypassed by a row written by an older build or restored from a backup. Two *call sites* is the
 * design; two *implementations* was an accident, and the failure mode is somebody widening one of
 * them and leaving the other behind.
 *
 * ### Fail closed
 *
 * A URL that will not parse is refused. That is stricter than the string-slicing version this
 * replaced, deliberately: for a check whose job is to keep `javascript:`, `intent:`, `file:` and
 * `content:` away from an Intent, "I could not tell what this is" has to mean no. Losing a
 * malformed link a browser might have salvaged is the cheaper mistake.
 *
 * ### The scheme alone is not enough
 *
 * `http:foo` and `https:///path` both parse, and both report a scheme of `http`/`https` with no
 * authority at all. Passing them produced a chip that opened nothing — a dead control, which is
 * the outcome the fail-closed rule above exists to avoid. So an authority is required too.
 *
 * The check is on **authority, not host**: [URI.getHost] returns null for an internationalised
 * name like `https://例え.テスト`, which is a perfectly good URL that a browser resolves. Requiring
 * a host would silently drop every IDN link.
 *
 * [URI] rather than `android.net.Uri` so this stays pure JVM and testable without Robolectric.
 */
fun String.isBrowsableHttpUrl(): Boolean {
    val uri = try {
        URI(this)
    } catch (e: URISyntaxException) {
        return false
    }
    val scheme = uri.scheme
    val isHttp = scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
    return isHttp && !uri.authority.isNullOrEmpty()
}

/**
 * The host of a URL, for use as a human-readable label — lowercased, `www.` stripped, null when
 * there isn't one.
 *
 * [URI.getHost] handles the cases hand-rolled slicing gets wrong, and one of them matters beyond
 * tidiness: a URL carrying userinfo (`https://user@host/`) sliced naively yields `user@host`,
 * which puts a username on screen.
 *
 * It returns null for internationalised names, though, so those fall back to the raw authority
 * with userinfo and port removed by hand — otherwise an IDN link would be labelled with its whole
 * URL. The port is only stripped when what follows the final colon is all digits, so an IPv6
 * literal keeps its address.
 *
 * Lowercased because hostnames are case-insensitive and `WWW.Example.TEST` is the same place as
 * `www.example.test` — which is also what makes the `www.` strip work regardless of the case it
 * arrived in. Kotlin's no-arg `lowercase()` is the locale-invariant mapping, so this is safe under
 * a Turkish locale.
 *
 * Never throws. A label is not worth an exception, and the caller has a sensible fallback.
 */
fun String.browsableHostOrNull(): String? {
    val uri = try {
        URI(this)
    } catch (e: URISyntaxException) {
        return null
    }
    val host = uri.host ?: uri.authority?.stripUserInfoAndPort()
    return host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
}

/**
 * Turns an authority into a bare hostname.
 *
 * Only used for the internationalised case [URI.getHost] refuses to parse, so it does by hand what
 * `getHost` would otherwise have done: drop `user:password@`, drop a trailing `:port`.
 */
private fun String.stripUserInfoAndPort(): String {
    val withoutUserInfo = substringAfterLast('@')
    val colon = withoutUserInfo.lastIndexOf(':')
    val looksLikePort = colon > 0 && withoutUserInfo.substring(colon + 1).let {
        it.isNotEmpty() && it.all(Char::isDigit)
    }
    return if (looksLikePort) withoutUserInfo.substring(0, colon) else withoutUserInfo
}
