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
 * [URI] rather than `android.net.Uri` so this stays pure JVM and testable without Robolectric —
 * and it is the parser, not hand-rolled slicing, that makes the scheme rule mean what it says.
 */
fun String.isBrowsableHttpUrl(): Boolean {
    val scheme = try {
        URI(this).scheme
    } catch (e: URISyntaxException) {
        return false
    }
    return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
}

/**
 * The host of a URL, for use as a human-readable label — `www.` stripped, null when there isn't one.
 *
 * [URI] handles the cases hand-rolled slicing gets wrong, and one of them matters beyond tidiness:
 * a URL carrying userinfo (`https://user@host/`) sliced naively yields `user@host`, which puts a
 * username on screen. Ports, IPv6 literals and query strings are the ordinary rest.
 *
 * Never throws. A label is not worth an exception, and the caller has a sensible fallback.
 */
fun String.browsableHostOrNull(): String? =
    try {
        URI(this).host?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
    } catch (e: URISyntaxException) {
        null
    }
