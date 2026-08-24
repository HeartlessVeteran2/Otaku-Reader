package app.otakureader.core.js.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the packaging of the compatibility prelude and the names it is required to publish.
 *
 * The prelude's *behaviour* cannot be exercised here: QuickJS ships as an Android artifact, so
 * there is no engine to evaluate it in from a JVM unit test. What this file can do is catch the
 * two failures that would otherwise reach a user as a broken source with a misleading message —
 * the resource not being packaged at all, and a rename that quietly drops one of the globals the
 * published extensions name.
 *
 * Both are worth a test precisely because neither fails at build time.
 */
class JsPreludeTest {

    @Test
    fun `the prelude is packaged and readable as a resource`() {
        // Missing packaging would surface as every extension failing with "MProvider is not
        // defined" — an error that names the script and implicates the source, when the fault is
        // entirely in this app's own build.
        assertTrue("the prelude resource is empty", JsPrelude.source.isNotEmpty())
    }

    @Test
    fun `the prelude publishes every global the published extensions rely on`() {
        // Each of these was verified against the JavaScript sources in the Mangayomi index rather
        // than taken from its contributing guide, which disagrees with the code in at least one
        // place. `MProvider` is the one with the worst failure mode: extensions name it in an
        // `extends` clause, which resolves when the class is *defined*, so its absence fails the
        // whole script rather than the one method that needed it.
        val required = listOf(
            "global.MProvider = MProvider;",
            "global.Client = MClient;",
            "global.Document = MElement;",
            "global.SharedPreferences = MSharedPreferences;",
        )

        val missing = required.filterNot { JsPrelude.source.contains(it) }

        assertTrue("the prelude no longer publishes: $missing", missing.isEmpty())
    }

    @Test
    fun `every parsed document is released on the path that throws`() {
        // The handle pool is capped and the host refuses rather than evicts once it is full, so a
        // selector that throws without releasing leaks one slot per failure until the source dies
        // with an error about documents rather than about the selector that actually broke.
        //
        // Asserting on the `finally` is crude, but the alternative is no coverage at all: the
        // leak only manifests against a page with enough rows to exhaust 32 handles, which is
        // exactly the size of input a unit test does not use.
        val withHandle = JsPrelude.source
            .substringAfter("function withHandle(")
            .substringBefore("MElement.prototype.select")

        val finallyAt = withHandle.indexOf(FINALLY)
        assertTrue("withHandle no longer has a finally block", finallyAt >= 0)

        // The release must sit inside the finally *body*, not merely somewhere after the keyword.
        // An earlier version of this test compared raw indices, which would have passed with the
        // release moved below the closing brace — and that is precisely the regression it exists
        // to catch, since the happy path releases either way and only the throwing path leaks.
        val body = blockBodyAt(withHandle, finallyAt + FINALLY.length - 1)

        assertTrue(
            "hostDocument.release(handle) must sit inside the finally block",
            body.contains(RELEASE),
        )
    }

    @Test
    fun `a request that never reached a server throws instead of yielding an empty body`() {
        // JsHttpBridge leaves `code` at 0 only when no HTTP response was obtained at all, and
        // carries the real status on every path that did get one. The prelude keys on that,
        // because handing such a response to a source means `body === ''`, which becomes
        // `JSON.parse('')` and an "Unexpected end of JSON input" naming neither the URL nor the
        // cause the bridge already knew.
        val decode = JsPrelude.source
            .substringAfter("function decodeResponse(")
            .substringBefore("function MClient(")

        val guardAt = decode.indexOf("if (raw.ok === false")
        assertTrue("decodeResponse no longer guards the no-response case", guardAt >= 0)

        val guard = blockBodyAt(decode, decode.indexOf('{', guardAt))
        assertTrue("the no-response guard must throw", guard.contains("throw new Error("))

        // The condition must narrow on `code`, not on `ok` alone. Widening it to every
        // unsuccessful response is the tempting simplification and it breaks the sources that
        // branch on `statusCode` for a genuine 404 or 403 — they would never see the status,
        // because the throw would fire first.
        // To the opening brace, not to the first `)`. Slicing at the first `)` happens to
        // capture the whole condition today only because the inner group opens before anything
        // closes; re-parenthesising it as `if ((raw.ok === false) && raw.code === 0)` would cut
        // the slice at `if ((raw.ok === false)` and silently stop exercising the real condition,
        // which is the one thing this assertion exists to do.
        val condition = decode.substring(guardAt, decode.indexOf('{', guardAt))
        assertTrue(
            "the guard must narrow on `code`, or a real 404 stops reaching the source: $condition",
            condition.contains("raw.code"),
        )
    }

    @Test
    fun `both client methods hand the request context to the decoder`() {
        // The guard's message is only as good as its wiring. `decodeResponse` reads method and
        // URL from its own parameters, so a call site reverted to the bare `.then(decodeResponse)`
        // still throws — but with "GET <unknown url>", which is the diagnosis this change exists
        // to provide. Asserting the guard alone would not notice that.
        for (method in listOf("get", "post")) {
            val body = JsPrelude.source
                .substringAfter("MClient.prototype.$method = function (")
                .substringBefore("};")

            assertTrue(
                "MClient.$method no longer routes through decodeResponse",
                body.contains("decodeResponse("),
            )
            assertTrue(
                "MClient.$method must pass the method and url into decodeResponse",
                body.contains("'${method.uppercase()}', url)"),
            )
        }
    }

    /**
     * The text between the brace at [openBraceIndex] and its match.
     *
     * Brace counting, not `substringBefore("}")`: the finally body is one statement today, and a
     * naive scan to the first `}` would keep passing while quietly stopping at the wrong place the
     * moment anyone wraps the release in a guard.
     */
    private fun blockBodyAt(source: String, openBraceIndex: Int): String {
        var depth = 0
        for (index in openBraceIndex until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(openBraceIndex + 1, index)
                }
            }
        }
        return ""
    }

    private companion object {
        const val FINALLY = "finally {"
        const val RELEASE = "hostDocument.release(handle)"
    }
}
