package app.otakureader.core.js.engine

/**
 * The Mangayomi compatibility layer, loaded from `resources/js/prelude.js`.
 *
 * Held as a Java resource rather than an Android asset so that it can be read with no [android
 * .content.Context]. [QuickJsHost] is constructed with a config, a script and an HTTP callback and
 * nothing else, and threading a Context through it purely to reach an asset would put an Android
 * dependency into the one part of this module that is otherwise plain Kotlin — and therefore the
 * one part that a JVM unit test can exercise directly.
 *
 * Read once and cached: the file is a few kilobytes, but a source's page-list call is preceded by
 * a detail call and a search, and re-reading a stream per call for a constant is waste that grows
 * with how heavily a source is used.
 */
internal object JsPrelude {

    private const val RESOURCE_PATH = "/js/prelude.js"

    /**
     * Fails loudly if the resource is missing.
     *
     * A packaging mistake that dropped this file would otherwise surface as every extension
     * failing with `ReferenceError: MProvider is not defined` — an error that names the script and
     * implicates the source, when the fault is entirely in the app's own build.
     */
    val source: String by lazy {
        requireNotNull(JsPrelude::class.java.getResourceAsStream(RESOURCE_PATH)) {
            "Missing $RESOURCE_PATH — the JavaScript compatibility prelude was not packaged"
        }.use { it.readBytes().decodeToString() }
    }
}
