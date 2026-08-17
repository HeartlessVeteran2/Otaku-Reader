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

        assertTrue(
            "withHandle must release its handle in a finally block",
            withHandle.contains("finally") && withHandle.contains("hostDocument.release(handle)"),
        )
    }
}
