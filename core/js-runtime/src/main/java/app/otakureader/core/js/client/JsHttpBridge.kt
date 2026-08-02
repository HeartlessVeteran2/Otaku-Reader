package app.otakureader.core.js.client

import app.otakureader.core.js.ipc.IJsHttpBridge
import app.otakureader.core.js.protocol.JsHttpRequest
import app.otakureader.core.js.protocol.JsHttpResponse
import app.otakureader.core.js.protocol.JsProtocol
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes HTTP on behalf of the sidecar, in the main process, on the app's shared client.
 *
 * Keeping network here rather than in the engine is a security decision, not a convenience
 * one. It means every request a JavaScript source makes inherits certificate pinning, rate
 * limiting, the cookie jar and any WebView-synced cookies/User-Agent — and, just as
 * importantly, that a compromised sidecar has no network path of its own to fall back on.
 *
 * Runs on binder threads, so it must be thread-safe. OkHttpClient is, by design.
 */
@Singleton
class JsHttpBridge @Inject constructor(
    private val client: OkHttpClient,
) {

    private companion object {
        /**
         * Ceiling on a single response body held in memory.
         *
         * A source asking for something enormous is either broken or hostile; either way the
         * main process must not be the thing that dies for it.
         */
        const val MAX_BODY_BYTES = 32L * 1024 * 1024

        val ALLOWED_SCHEMES = setOf("https")
    }

    val binder: IJsHttpBridge.Stub = object : IJsHttpBridge.Stub() {
        override fun execute(requestJson: String): String {
            val response = runCatching {
                val request = JsProtocol.json.decodeFromString<JsHttpRequest>(requestJson)
                perform(request)
            }.getOrElse {
                JsHttpResponse(ok = false, error = it.message ?: "Request failed")
            }
            return JsProtocol.json.encodeToString(response)
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

        val builder = Request.Builder().url(url)
        request.headers.forEach { (name, value) -> builder.addHeader(name, value) }

        when (request.method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post((request.body ?: "").toRequestBody())
            else -> return JsHttpResponse(ok = false, error = "Unsupported method ${request.method}")
        }

        return client.newCall(builder.build()).execute().use { response ->
            val declaredLength = response.body?.contentLength() ?: 0L
            if (declaredLength > MAX_BODY_BYTES) {
                return@use JsHttpResponse(
                    ok = false,
                    code = response.code,
                    error = "Response declares $declaredLength bytes, over the $MAX_BODY_BYTES limit",
                )
            }

            // peekBody caps what is pulled into memory. contentLength alone is not enough —
            // it reports -1 for a chunked response, which is exactly how an unbounded stream
            // would arrive.
            val body = runCatching { response.peekBody(MAX_BODY_BYTES).string() }
                .getOrElse { return@use JsHttpResponse(ok = false, code = response.code, error = it.message) }

            JsHttpResponse(
                ok = response.isSuccessful,
                code = response.code,
                headers = response.headers.toSingleValueMap(),
                body = body,
            )
        }
    }
}

/** Last value wins on repeated headers; the protocol carries a flat map, not a multimap. */
private fun Headers.toSingleValueMap(): Map<String, String> =
    (0 until size).associate { name(it) to value(it) }
