package app.otakureader.core.js.client

import android.os.ParcelFileDescriptor
import app.otakureader.core.js.ipc.IJsHttpBridge
import app.otakureader.core.js.protocol.JsHttpRequest
import app.otakureader.core.js.protocol.JsHttpResponse
import app.otakureader.core.js.protocol.JsProtocol
import app.otakureader.core.js.protocol.writeToPipe
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes HTTP on behalf of the sidecar, in the main process, on the app's shared client.
 *
 * The engine runs in an isolated process with no INTERNET permission, so this is the *only*
 * network path a JavaScript source has. Every request therefore inherits certificate pinning,
 * rate limiting and the cookie jar by construction rather than by convention.
 *
 * Runs on binder threads, so it must be thread-safe. OkHttpClient is, by design.
 */
@Singleton
class JsHttpBridge @Inject constructor(
    sharedClient: OkHttpClient,
) {

    /**
     * The app's shared client, with automatic redirect following disabled.
     *
     * Built with newBuilder() so every interceptor, the cookie jar, the certificate pinner and
     * the connection pool are all inherited — only redirect handling changes, because hops have
     * to be validated before they are issued (see [followManually]).
     */
    private val client: OkHttpClient = sharedClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private companion object {
        /**
         * Ceiling on a single response body held in memory.
         *
         * A source asking for something enormous is either broken or hostile; either way the
         * main process must not be the thing that dies for it.
         */
        const val MAX_BODY_BYTES = 8L * 1024 * 1024

        val ALLOWED_SCHEMES = setOf("https")

        /** Bounds a redirect loop, since hops are now followed by hand. */
        const val MAX_REDIRECTS = 5

        /**
         * Headers that must not survive an origin change.
         *
         * These authenticate the caller to one specific origin. Forwarding them to another host
         * hands over the user's credentials for the first site — and since the redirect target
         * is chosen by the source, that host is attacker-controlled.
         */
        val CREDENTIAL_HEADERS = listOf("Authorization", "Proxy-Authorization", "Cookie")

        /**
         * Status codes that turn the follow-up into a bodyless GET.
         *
         * 301/302/303 change the method; 307/308 exist precisely to preserve it. Replaying a
         * POST body across a 302 can repeat a side effect the caller intended to happen once.
         */
        val METHOD_CHANGING_REDIRECTS = setOf(301, 302, 303)

        /** Entity headers that describe a body, and so are meaningless once it is dropped. */
        val ENTITY_HEADERS = listOf("Content-Type", "Content-Length", "Transfer-Encoding")

        /**
         * Hostnames a source may never reach.
         *
         * A JavaScript source can ask this bridge for any URL, which makes the bridge a
         * server-side-request-forgery primitive: without this, a hostile extension could use
         * the app as a probe for whatever the device can reach — router admin pages, other
         * services on the LAN, cloud metadata endpoints — none of which the user intended to
         * expose by installing a manga source.
         *
         * Blocking by address range rather than by allow-listing the source's own host, because
         * legitimate sources fan out across CDNs, image hosts and API subdomains that cannot be
         * predicted from the base URL.
         */
        val BLOCKED_HOST_PATTERNS = listOf(
            Regex("""^localhost$""", RegexOption.IGNORE_CASE),
            Regex("""^127\."""),
            Regex("""^0\."""),
            Regex("""^10\."""),
            Regex("""^192\.168\."""),
            // 172.16.0.0/12
            Regex("""^172\.(1[6-9]|2\d|3[01])\."""),
            // Link-local, including the 169.254.169.254 cloud metadata address.
            Regex("""^169\.254\."""),
            // IPv6 loopback and unique-local.
            Regex("""^\[?::1\]?$"""),
            Regex("""^\[?f[cd][0-9a-f]{2}:""", RegexOption.IGNORE_CASE),
        )
    }

    val binder: IJsHttpBridge.Stub = object : IJsHttpBridge.Stub() {
        override fun execute(requestJson: String): ParcelFileDescriptor {
            val response = runCatching {
                perform(JsProtocol.json.decodeFromString<JsHttpRequest>(requestJson))
            }.getOrElse {
                JsHttpResponse(ok = false, error = it.message ?: "Request failed")
            }
            return writeToPipe(JsProtocol.json.encodeToString(response))
        }
    }

    private fun perform(request: JsHttpRequest): JsHttpResponse {
        // HTTPS only. The APK extension installer already refuses plain HTTP, and a JS source
        // is no more trusted than an APK one — an extension that wants cleartext is either
        // broken or trying to be intercepted.
        val url = request.url.toHttpUrlOrNull()
            ?: return JsHttpResponse(ok = false, error = "Malformed URL")
        if (url.scheme !in ALLOWED_SCHEMES) {
            return JsHttpResponse(ok = false, error = "Refused non-HTTPS request to ${url.host}")
        }
        if (isBlocked(url)) {
            return JsHttpResponse(ok = false, error = "Refused request to private address ${url.host}")
        }

        val builder = Request.Builder().url(url)
        request.headers.forEach { (name, value) -> builder.addHeader(name, value) }

        when (request.method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post((request.body ?: "").toRequestBody())
            else -> return JsHttpResponse(ok = false, error = "Unsupported method ${request.method}")
        }

        return followManually(builder.build())
    }

    /**
     * Issue the request, validating every redirect hop *before* following it.
     *
     * Redirect following is disabled on this client rather than inspecting the final URL after
     * the fact. Checking afterwards is not a defence: OkHttp would already have connected to
     * the internal host and received its response, so the probe — the entire behaviour the
     * blocklist exists to prevent — has happened. Suppressing the body at that point only hides
     * the result from the script; a timing difference still reveals whether the host is alive.
     *
     * Validating each hop before issuing it is what actually closes the path.
     */
    private fun followManually(initial: Request): JsHttpResponse {
        var request = initial

        repeat(MAX_REDIRECTS) {
            client.newCall(request).execute().use { response ->
                val location = response.header("Location")
                    ?.takeIf { response.isRedirect }
                    ?: return readBody(response)

                val next = response.request.url.resolve(location)
                    ?: return JsHttpResponse(
                        ok = false,
                        code = response.code,
                        error = "Redirect to a malformed URL",
                    )
                if (next.scheme !in ALLOWED_SCHEMES) {
                    return JsHttpResponse(
                        ok = false,
                        code = response.code,
                        error = "Refused redirect to non-HTTPS ${next.host}",
                    )
                }
                if (isBlocked(next)) {
                    return JsHttpResponse(
                        ok = false,
                        code = response.code,
                        error = "Refused redirect to private address ${next.host}",
                    )
                }
                request = buildRedirect(request, next, response.code)
            }
        }

        return JsHttpResponse(ok = false, error = "Too many redirects")
    }

    /**
     * Build the follow-up request for a redirect.
     *
     * Taking redirect handling away from OkHttp means also taking on the two rules it applies
     * for us, both of which are easy to lose and neither of which is cosmetic:
     *
     *  - **Credentials are stripped on an origin change.** Carrying the caller's `Authorization`
     *    or `Cookie` to a different host hands the user's credentials for one site to whatever
     *    the redirect points at — which, since the source chooses the URL, is attacker-
     *    controlled. The cookie jar still supplies the *new* origin's own cookies; only headers
     *    the script set explicitly are dropped.
     *  - **Method and body follow the status code.** 301/302/303 become a bodyless GET, 307/308
     *    preserve both. Replaying a POST body to every redirect can repeat a side effect the
     *    caller intended once.
     */
    private fun buildRedirect(current: Request, next: HttpUrl, code: Int): Request {
        val builder = current.newBuilder().url(next)

        if (isOriginChange(current.url, next)) {
            CREDENTIAL_HEADERS.forEach { builder.removeHeader(it) }
        }

        if (changesMethodToGet(code)) {
            builder.method("GET", null)
            ENTITY_HEADERS.forEach { builder.removeHeader(it) }
        }

        return builder.build()
    }

    /**
     * Read the body, refusing rather than silently truncating.
     *
     * `peekBody` caps what is pulled into memory, but a capped read is indistinguishable from a
     * complete one — a source would receive half a page of HTML alongside a 200 and parse the
     * fragment as if it were the whole document, producing wrong results that look successful.
     * Reading one byte past the limit makes the overflow detectable, so it can be reported as a
     * failure instead.
     */
    private fun readBody(response: okhttp3.Response): JsHttpResponse {
        val declared = response.body?.contentLength() ?: 0L
        if (declared > MAX_BODY_BYTES) {
            return JsHttpResponse(
                ok = false,
                code = response.code,
                error = "Response declares $declared bytes, over the $MAX_BODY_BYTES limit",
            )
        }

        val peeked = runCatching { response.peekBody(MAX_BODY_BYTES + 1).bytes() }
            .getOrElse { return JsHttpResponse(ok = false, code = response.code, error = it.message) }

        if (peeked.size > MAX_BODY_BYTES) {
            return JsHttpResponse(
                ok = false,
                code = response.code,
                error = "Response exceeds the $MAX_BODY_BYTES byte limit",
            )
        }

        return JsHttpResponse(
            ok = response.isSuccessful,
            code = response.code,
            headers = response.headers.toSingleValueMap(),
            body = peeked.toString(Charsets.UTF_8),
        )
    }

    private fun isBlocked(url: HttpUrl): Boolean =
        BLOCKED_HOST_PATTERNS.any { it.containsMatchIn(url.host) }
}

/** Last value wins on repeated headers; the protocol carries a flat map, not a multimap. */
private fun Headers.toSingleValueMap(): Map<String, String> =
    (0 until size).associate { name(it) to value(it) }


// ---------------------------------------------------------------------------------------
// Redirect policy
// ---------------------------------------------------------------------------------------
//
// Split out from the bridge so they can be tested directly: JsHttpBridge instantiates an AIDL
// Stub at construction, which needs a real Binder and therefore a device. These two predicates
// carry the security-relevant decisions, so they are the part that most needs covering.

/** True when the redirect target is a different origin, by scheme, host or port. */
internal fun isOriginChange(from: HttpUrl, to: HttpUrl): Boolean =
    from.scheme != to.scheme || from.host != to.host || from.port != to.port

/**
 * True when the status code turns the follow-up into a bodyless GET.
 *
 * 301/302/303 change the method; 307 and 308 exist specifically to preserve it.
 */
internal fun changesMethodToGet(code: Int): Boolean = code == 301 || code == 302 || code == 303
