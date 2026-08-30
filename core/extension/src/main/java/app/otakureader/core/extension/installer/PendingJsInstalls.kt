package app.otakureader.core.extension.installer

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * A durable record of JavaScript installs that started but have not been confirmed (#1229, item 2a).
 *
 * ## The window this closes
 *
 * Installing a JavaScript source writes to two stores: the script (plus anything the source saves,
 * including logins) under `filesDir/js-exts/`, and the `extensions` row in Room. Uninstall finds
 * its target *through the database*, so a script with no row is a source that still executes and
 * can never be removed.
 *
 * `ExtensionInstaller` already closes every in-process failure between those two writes. The one
 * it cannot close is process death: if the app is killed after the script is registered but before
 * the row is written, no `finally` and no `catch` ever runs. Low likelihood, unbounded consequence
 * — the orphan is permanent, and it is a source with the user's credentials in it.
 *
 * A marker on disk survives that, because it is written *before* the install and removed only once
 * the outcome is settled. Anything still marked at the next launch is an install nothing finished.
 *
 * ## Why the file is named by a hash and holds the id
 *
 * A source id is not a safe filename — it can contain `/`, and Mangayomi ids routinely do. Encoding
 * it would work, but the decode is then load-bearing for a *destructive* sweep, and a wrong decode
 * deletes the wrong source. Naming the file by the SHA-256 of the id makes lookup and deletion
 * deterministic without needing to decode anything, and storing the id verbatim inside means the
 * sweep reads back exactly what was written rather than reconstructing it.
 */
internal class PendingJsInstalls(context: Context) {

    private val dir = File(context.filesDir, DIR_NAME)

    /**
     * Records that [sourceId] is about to be installed. Returns false if the marker could not be
     * written.
     *
     * The caller must **fail closed** on false. This mirrors the decision already made for the
     * pre-install row lookup: never create an artifact whose ownership cannot later be
     * established. An install the user can retry is much cheaper than a source that executes and
     * cannot be removed.
     */
    fun begin(sourceId: String): Boolean = runCatching {
        dir.mkdirs()
        markerFor(sourceId).writeText(sourceId)
        true
    }.getOrDefault(false)

    /**
     * Clears the marker for [sourceId], whatever the install's outcome was.
     *
     * Called for failures too, not only successes: by the time the installer has finished
     * reconciling, the two stores agree and there is nothing left for a sweep to do. Leaving the
     * marker would make the next launch re-examine a resolved install and, finding no row for a
     * legitimately failed install, delete a script that reconciliation had already dealt with.
     */
    fun finish(sourceId: String) {
        runCatching { markerFor(sourceId).delete() }
    }

    /** Source ids whose install was started and never confirmed. */
    fun pending(): List<String> = runCatching {
        dir.listFiles()
            ?.filter { it.isFile }
            ?.mapNotNull { file -> runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } }
            .orEmpty()
    }.getOrDefault(emptyList())

    private fun markerFor(sourceId: String) = File(dir, sha256Hex(sourceId))

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIR_NAME = "js-installs-pending"
    }
}
