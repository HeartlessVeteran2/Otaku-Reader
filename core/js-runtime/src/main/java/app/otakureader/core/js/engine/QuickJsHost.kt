package app.otakureader.core.js.engine

import app.otakureader.core.js.protocol.JsCallArgs
import app.otakureader.core.js.protocol.JsHttpRequest
import app.otakureader.core.js.protocol.JsHttpResponse
import app.otakureader.core.js.protocol.JsProtocol
import app.otakureader.core.js.protocol.JsSourceConfig
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * One loaded JavaScript source and the QuickJS context backing it.
 *
 * Runs **inside the sidecar process only**. Nothing here is safe to call from the main
 * process, because the whole design assumes this object can be destroyed at any moment by the
 * process being killed.
 *
 * ### What the script can reach
 *
 * A QuickJS context starts with no I/O of any kind — no filesystem, no network, no clock
 * beyond `Date`. Capability arrives solely through the globals installed below, so the set of
 * things an extension can do is exactly the set defined here and nothing else. In particular
 * there is no reflective host-object binding: a script cannot reach a Kotlin class that was
 * not explicitly handed to it.
 *
 * ### What still cannot be contained
 *
 * [QuickJs.memoryLimit] and [QuickJs.maxStackSize] bound allocation and recursion, but no
 * Android QuickJS binding exposes `JS_SetInterruptHandler`, so a non-allocating infinite loop
 * cannot be stopped from in here. That is why this class lives in a disposable process: the
 * client's wall-clock budget is enforced by killing it.
 */
internal class QuickJsHost(
    private val config: JsSourceConfig,
    private val script: String,
    private val http: (JsHttpRequest) -> JsHttpResponse,
) {

    private companion object {
        /**
         * Caps runaway allocation. Generous enough for a large chapter's HTML, small enough
         * that a leaking script trips it long before the sidecar becomes a memory problem for
         * the device as a whole.
         */
        const val MEMORY_LIMIT_BYTES = 64L * 1024 * 1024

        /** Bounds runaway recursion, which would otherwise crash the process natively. */
        const val MAX_STACK_SIZE_BYTES = 1L * 1024 * 1024
    }

    /** Parsed documents held by handle so JS never receives a host object reference. */
    private val documents = mutableMapOf<Int, Document>()
    private var nextDocumentHandle = 1

    private val mutablePreferences = config.preferences.toMutableMap()

    /**
     * Evaluate [method] against the loaded script and return its raw JSON result.
     *
     * Suspends because HTTP is asynchronous; the sidecar's binder thread blocks on it, which is
     * correct — the client is holding a wall-clock budget over this exact call.
     */
    suspend fun call(method: String, args: JsCallArgs): String {
        return QuickJs.create(Dispatchers.Default).use { engine ->
            engine.memoryLimit = MEMORY_LIMIT_BYTES
            engine.maxStackSize = MAX_STACK_SIZE_BYTES

            installGlobals(engine)
            engine.evaluate<Any?>(script)

            val invocation = buildInvocation(method, args)
            val result = engine.evaluate<Any?>(invocation)
            result?.toString() ?: "null"
        }
    }

    /**
     * A fresh context per call, rather than one per source held open.
     *
     * Deliberate: it means a script cannot accumulate state between calls, so one page of
     * results cannot poison the next, and a leaked reference cannot outlive the call that made
     * it. Context creation is microseconds against a call that is about to do network I/O.
     */
    private fun installGlobals(engine: QuickJs) {
        installClient(engine)
        installDocument(engine)
        installPreferences(engine)
    }

    private fun installClient(engine: QuickJs) {
        engine.define("Client") {
            asyncFunction("get") { args ->
                request("GET", args)
            }
            asyncFunction("post") { args ->
                request("POST", args)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun request(method: String, args: Array<Any?>): String {
        val url = args.getOrNull(0) as? String
            ?: error("Client.$method requires a url")
        val headers = (args.getOrNull(1) as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                key to v.toString()
            }
            ?.toMap()
            .orEmpty()
        val body = args.getOrNull(2) as? String

        val response = http(JsHttpRequest(url = url, method = method, headers = headers, body = body))
        return JsProtocol.json.encodeToString(response)
    }

    /**
     * Jsoup exposed by integer handle rather than by object.
     *
     * Handing a script a live [Document] would give it a Kotlin object graph to walk; a handle
     * gives it an opaque integer that means nothing outside this map. It also keeps every
     * value crossing the JS boundary a primitive, which is the only thing the binding layer
     * converts reliably.
     */
    private fun installDocument(engine: QuickJs) {
        engine.define("Document") {
            function("parse") { args ->
                val html = args.getOrNull(0) as? String ?: ""
                val handle = nextDocumentHandle++
                documents[handle] = Jsoup.parse(html, config.baseUrl)
                handle
            }
            function("select") { args ->
                val doc = documents[(args.getOrNull(0) as? Number)?.toInt()]
                val selector = args.getOrNull(1) as? String ?: return@function emptyList<String>()
                doc?.select(selector)?.map { it.outerHtml() } ?: emptyList<String>()
            }
            function("selectFirst") { args ->
                val doc = documents[(args.getOrNull(0) as? Number)?.toInt()]
                val selector = args.getOrNull(1) as? String ?: return@function null
                doc?.selectFirst(selector)?.outerHtml()
            }
            function("text") { args ->
                val html = args.getOrNull(0) as? String ?: return@function ""
                Jsoup.parse(html).text()
            }
            function("attr") { args ->
                val html = args.getOrNull(0) as? String ?: return@function ""
                val name = args.getOrNull(1) as? String ?: return@function ""
                // absUrl resolves relative hrefs against the source's baseUrl, which is what
                // essentially every extension actually wants from an href or src.
                val element = Jsoup.parse(html, config.baseUrl).body().children().firstOrNull()
                element?.absUrl(name).takeUnless { it.isNullOrEmpty() }
                    ?: element?.attr(name).orEmpty()
            }
            function("release") { args ->
                documents.remove((args.getOrNull(0) as? Number)?.toInt())
                null
            }
        }
    }

    /**
     * Source preferences, scoped to this source alone.
     *
     * The map is per-[QuickJsHost] and seeded from this source's own stored values, so one
     * source cannot read another's — which matters because these frequently hold site
     * credentials.
     */
    private fun installPreferences(engine: QuickJs) {
        engine.define("SharedPreferences") {
            function("get") { args ->
                val key = args.getOrNull(0) as? String ?: return@function null
                mutablePreferences[key]
            }
            function("set") { args ->
                val key = args.getOrNull(0) as? String ?: return@function null
                mutablePreferences[key] = args.getOrNull(1)?.toString().orEmpty()
                null
            }
        }
    }

    /** Preference writes made by the script, for the client to persist. */
    fun dirtyPreferences(): Map<String, String> = mutablePreferences.toMap()

    /**
     * Build the JS expression that invokes the extension.
     *
     * Arguments are injected as JSON literals rather than string-concatenated, so a quote or
     * backslash in a search query cannot terminate the literal and change the expression being
     * evaluated — the JS equivalent of an injection.
     */
    private fun buildInvocation(method: String, args: JsCallArgs): String {
        val enc = { value: String -> JsProtocol.json.encodeToString(value) }
        val call = when (method) {
            JsProtocol.Method.POPULAR -> "getPopular(${args.page ?: 1})"
            JsProtocol.Method.LATEST -> "getLatestUpdates(${args.page ?: 1})"
            JsProtocol.Method.SEARCH ->
                "search(${enc(args.query.orEmpty())}, ${args.page ?: 1}, ${args.filters ?: "[]"})"
            JsProtocol.Method.DETAIL -> "getDetail(${enc(args.url.orEmpty())})"
            JsProtocol.Method.PAGE_LIST -> "getPageList(${enc(args.url.orEmpty())})"
            JsProtocol.Method.HTML_CONTENT -> "getHtmlContent(${enc(args.url.orEmpty())})"
            JsProtocol.Method.FILTER_LIST -> "getFilterList()"
            else -> error("Unknown source method: $method")
        }
        // The extension declares `class DefaultExtension extends MProvider`; results are
        // stringified here so exactly one type crosses the boundary regardless of method.
        return """
            (async () => {
                const provider = new DefaultExtension();
                const result = await provider.$call;
                return JSON.stringify(result === undefined ? null : result);
            })()
        """.trimIndent()
    }
}

