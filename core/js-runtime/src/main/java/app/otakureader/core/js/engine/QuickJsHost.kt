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
 * ### Limits, and why the process boundary still exists
 *
 * [QuickJs.memoryLimit] and [QuickJs.maxStackSize] bound allocation and recursion, and
 * `evaluationTimeoutMillis` stops a non-yielding script from inside the VM.
 *
 * The sidecar process is NOT redundant given that timeout. It does a different job: an
 * isolated process runs under a permission-less UID, so a script that escapes the QuickJS
 * sandbox entirely — through a bug in the engine rather than in JavaScript — still has no
 * network, no filesystem and no access to app data. The timeout handles misbehaving scripts;
 * the process handles a compromised engine. Neither substitutes for the other.
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

        /**
         * In-VM evaluation budget — the first line of defence against a non-yielding script.
         *
         * Deliberately shorter than the client's process-level budget so that the cheap remedy
         * gets to act first: interrupting the VM costs nothing and leaves the engine usable,
         * whereas killing the process forces a rebind and a reload of every registered source.
         * The process kill remains as the backstop for the case this cannot cover — the engine
         * wedging somewhere outside script evaluation.
         */
        const val EVALUATION_TIMEOUT_MS = 20_000L

        /**
         * Cap on live parsed documents.
         *
         * Jsoup documents live on the Android heap, not inside QuickJS, so [MEMORY_LIMIT_BYTES]
         * does not see them at all: a script looping on `Document.parse()` would exhaust the
         * process while the JS heap looked idle. Sources parse one or two documents per call,
         * so this bound is far above legitimate use and only trips on a leak.
         */
        const val MAX_LIVE_DOCUMENTS = 32

        /** Host function the invocation calls to hand its serialized result back. */
        const val RESULT_BINDING = "__otakuEmitResult"
    }

    /** Parsed documents held by handle so JS never receives a host object reference. */
    private val documents = mutableMapOf<Int, Document>()
    private var nextDocumentHandle = 1

    private val mutablePreferences = config.preferences.toMutableMap()

    /**
     * Preferences as the script left them, or null if it never wrote one.
     *
     * Read by the service after a call so the main process can persist the change. Null rather
     * than always returning the map, so the common case — a source that only reads — costs
     * nothing on the wire.
     */
    var changedPreferences: Map<String, String>? = null
        private set

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
            // Stops a non-yielding script from inside the VM, which the alpha line could not do.
            engine.evaluationTimeoutMillis = EVALUATION_TIMEOUT_MS

            installGlobals(engine)

            // Order matters in both directions. The prelude captures the host namespaces
            // installed above and then shadows their names with the class-shaped API extensions
            // expect, so it cannot run before `installGlobals`. It defines `MProvider`, which
            // every extension names in its `extends` clause, so it must run before the script is
            // evaluated — a class declaration resolves its base at definition time, not at call
            // time, and a missing base fails the whole script rather than one method.
            engine.evaluate<Any?>(sourceConfigGlobal())
            engine.evaluate<Any?>(JsPrelude.source)

            // The result is pushed out through a binding rather than taken from the
            // evaluation's return value.
            //
            // Two evaluation forms both fail here, in opposite ways. An async IIFE returns the
            // Promise object itself, so the caller gets "[object Promise]". A module has no
            // completion value at all, so the caller gets null. Since extension methods are
            // async, top-level await — which needs module mode — is the only way to resolve
            // them, and module mode is exactly the form that discards the value.
            //
            // Capturing through a host function sidesteps the question: the script hands the
            // JSON back explicitly, so nothing depends on what an evaluation form happens to
            // return.
            var captured: String? = null
            engine.function(RESULT_BINDING) { callbackArgs ->
                captured = callbackArgs.getOrNull(0) as? String
                null
            }

            engine.evaluate<Any?>(script)
            engine.evaluate<Any?>(buildInvocation(method, args), asModule = true)

            // A source that returned nothing at all is a real failure, not an empty result —
            // reporting it here names the engine/script contract rather than letting a null
            // surface later as a confusing decode error.
            captured ?: error("Source method $method produced no result")
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
                // Drop the header rather than stringifying a null into it.
                //
                // Sources build header maps from preferences that frequently have no value yet —
                // `{"user-agent": this.getPreference("custom_user_agent")}` is the common shape,
                // and on a fresh install that preference is unset. `Any?.toString()` renders that
                // as the four characters `null`, so the request goes out claiming a User-Agent of
                // "null": worse than sending none, because it defeats the default the shared
                // OkHttp client would otherwise have supplied and it is a value some sites
                // fingerprint on.
                val value = v ?: return@mapNotNull null
                key to value.toString()
            }
            ?.toMap()
            .orEmpty()
        val body = args.getOrNull(2) as? String

        val response = http(
            JsHttpRequest(
                url = url,
                method = method,
                headers = headers,
                body = body,
                // From the config, not from the script — this is what the bridge scopes cookies
                // by, so it must not be anything JavaScript can choose.
                sourceUrls = listOfNotNull(
                    config.baseUrl.takeIf { it.isNotBlank() },
                    config.apiUrl.takeIf { it.isNotBlank() },
                ),
            ),
        )
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
                // Refuse rather than evict: evicting would invalidate a handle the script still
                // holds, turning a leak into confusing "element not found" failures instead of
                // an explicit one.
                check(documents.size < MAX_LIVE_DOCUMENTS) {
                    "Source holds more than $MAX_LIVE_DOCUMENTS parsed documents; release them"
                }
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
            /**
             * The attribute exactly as written.
             *
             * This used to resolve every attribute through `absUrl`, with a comment explaining
             * that relative hrefs want resolving — true of an href, and applied to *all* names.
             * Jsoup's `absUrl` resolves any string against the base URI, so a title read with
             * `attr("title")` came back as `https://site/…` with the title as its path. Sources
             * read titles, alt text and data-* fields this way constantly, so they were being
             * handed URLs where they expected text. Use [absAttr] when a URL is what is wanted.
             */
            function("attr") { args ->
                val html = args.getOrNull(0) as? String ?: return@function ""
                val name = args.getOrNull(1) as? String ?: return@function ""
                Jsoup.parse(html, config.baseUrl).body().children().firstOrNull()
                    ?.attr(name).orEmpty()
            }
            /** The attribute resolved against the source's base URL, for href/src and the like. */
            function("absAttr") { args ->
                val html = args.getOrNull(0) as? String ?: return@function ""
                val name = args.getOrNull(1) as? String ?: return@function ""
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
     *
     * A script can read back its own write within the same call, which is what sources rely on
     * (they typically resolve a mirror domain and then use it). Writes also survive the call:
     * the mutated map is published through [changedPreferences], returned to the main process in
     * `JsCallResult.preferences`, and persisted there. The sidecar itself stores nothing — it
     * has no filesystem access at all — so the round-trip is the only way a write can last.
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
                // Snapshot rather than alias the mutable map: the service reads this after the
                // call, and handing out the live map would let a later write mutate a value the
                // caller believes it already captured.
                changedPreferences = mutablePreferences.toMap()
                null
            }
        }
    }

    /**
     * Publish this source's own manifest as a JSON literal for the prelude to read.
     *
     * Extensions reach for `this.source.baseUrl`, `this.source.apiUrl` and `this.source.lang`
     * throughout — an API-backed source builds essentially every request from `apiUrl`. Handing
     * the config over as text rather than as a binding keeps the rule that only primitives cross
     * the boundary, and encoding it with the serializer rather than by concatenation means a
     * quote or backslash in a source name cannot terminate the literal and change the program
     * being evaluated, which is the same reasoning [buildInvocation] applies to call arguments.
     *
     * Preferences are deliberately excluded: they are reachable through `SharedPreferences`,
     * which routes writes back to the main process, whereas a copy pasted into this object would
     * be a snapshot that silently stopped matching after the first write.
     */
    private fun sourceConfigGlobal(): String {
        // Two encodings, one runtime JSON layer — they are not the same kind of step, and reading
        // them as two semantic layers is the obvious misreading.
        //
        //   1. the config object  -> JSON text            `{"id":"x","baseUrl":"https://…"}`
        //   2. that JSON text     -> a JS string literal  `"{\"id\":\"x\",…}"`
        //
        // Step 2 is source-code quoting, not data. The JavaScript parser undoes it while
        // evaluating the assignment, so at runtime the global holds a *string* of JSON and the
        // prelude's single `JSON.parse` yields the object. Parsing twice would throw on the
        // resulting object; emitting the manifest bare would drop the quoting that keeps a quote
        // or backslash in a source's name from terminating the literal and changing the program —
        // the same reasoning [buildInvocation] applies to call arguments.
        val manifest = JsProtocol.json.encodeToString(config.copy(preferences = emptyMap()))
        return "globalThis.__otakuSourceConfig = ${JsProtocol.json.encodeToString(manifest)};"
    }

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
        // Top-level await, so module mode is required — extension methods are async and this is
        // the only form that can resolve them. The value is handed back through the host
        // binding rather than returned, since a module evaluates to nothing.
        //
        // The extension declares `class DefaultExtension extends MProvider`; results are
        // stringified here so exactly one type crosses the boundary regardless of method.
        return """
            const provider = new DefaultExtension();
            const result = await provider.$call;
            $RESULT_BINDING(JSON.stringify(result === undefined ? null : result));
        """.trimIndent()
    }
}

