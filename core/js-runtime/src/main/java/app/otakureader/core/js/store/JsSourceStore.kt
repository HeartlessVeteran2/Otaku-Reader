package app.otakureader.core.js.store

import android.content.Context
import app.otakureader.core.js.protocol.JsProtocol
import app.otakureader.core.js.protocol.JsSourceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** An installed JavaScript source: its manifest and the script itself. */
data class InstalledJsSource(
    val config: JsSourceConfig,
    val script: String,
)

/**
 * The on-disk record for one source: manifest and script in a single document.
 *
 * Deliberately one file rather than a `.json` beside a `.js`. Two files cannot be replaced
 * together, so an update that crashes — or merely gets read — between the two writes pairs the
 * old manifest with the new script, and the source then runs under configuration that does not
 * belong to it. Keeping both in one document makes a single atomic rename the whole update, so
 * a reader sees either the complete old version or the complete new one and never a mixture.
 *
 * This costs nothing in practice: [JsSourceStore.installed] already reads every script into
 * memory to hand to the engine, so nothing is loaded here that was not loaded before.
 */
@Serializable
private data class StoredJsSource(
    val config: JsSourceConfig,
    val script: String,
)

/**
 * On-disk storage for installed JavaScript sources.
 *
 * Each source is one file under `filesDir/js-exts/`. Installing is a file write — there is no
 * APK, no `PackageManager`, no signature dance and no system install prompt, which is the entire
 * reason this backend exists.
 */
@Singleton
class JsSourceStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    internal companion object {
        const val DIRECTORY = "js-exts"
        const val EXTENSION = "json"
        const val TEMP_SUFFIX = ".tmp"
    }

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun fileFor(sourceId: String) = File(directory, "${sourceId.toFileName()}.$EXTENSION")

    /**
     * Every installed source, skipping any that cannot be read.
     *
     * A source whose record is corrupt is dropped rather than thrown, because one bad file must
     * not take out the whole source list — that failure mode is precisely what made the APK
     * loader undiagnosable, where a single bad extension left Browse showing nothing.
     */
    suspend fun installed(): List<InstalledJsSource> = withContext(Dispatchers.IO) {
        directory.listFiles { file -> file.extension == EXTENSION }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    val stored = JsProtocol.json.decodeFromString<StoredJsSource>(file.readText())
                    InstalledJsSource(stored.config, stored.script)
                }.getOrNull()
            }
    }

    /**
     * Install or replace a source, atomically.
     *
     * Written to a temporary file and then renamed. Rename within a directory is atomic, so a
     * concurrent [installed] sees either the previous record or the new one — never a half-
     * written file, and never a new script under an old manifest.
     */
    suspend fun install(config: JsSourceConfig, script: String) = withContext(Dispatchers.IO) {
        val target = fileFor(config.id)
        val temp = File(target.parentFile, "${target.name}$TEMP_SUFFIX")

        temp.writeText(JsProtocol.json.encodeToString(StoredJsSource(config, script)))
        if (!temp.renameTo(target)) {
            // Rename can fail if the target is somehow unwritable. Leaving the temp file behind
            // would let it accumulate, and it must not be mistaken for an installed source —
            // which it cannot be, since listing filters on the .json extension.
            temp.delete()
            error("Could not install JavaScript source ${config.id}")
        }
    }

    suspend fun uninstall(sourceId: String) = withContext(Dispatchers.IO) {
        fileFor(sourceId).delete()
        Unit
    }
}

/**
 * Map a source id onto a safe, collision-resistant filename.
 *
 * Two separate problems are solved here, and both matter.
 *
 * **Traversal.** Source ids arrive from a remote index that nobody in this codebase controls,
 * and they are used to build paths. Interpolating one straight into a `File` makes it a path-
 * traversal primitive: an id of `../../databases/otaku` would resolve outside the extension
 * directory and let an install overwrite the app's own database.
 *
 * **Collision.** Sanitizing alone would map `a/b` and `a_b` onto one name, so the readable part
 * cannot be the identity. An earlier version appended `hashCode()`, which is not good enough:
 * it is a 32-bit non-cryptographic hash over a value an attacker chooses, so a malicious source
 * can be given an id built to collide with an existing one — taking over its script file and
 * its stored preferences, which routinely hold the user's login for that site. A truncated
 * readable prefix makes that easier still.
 *
 * So identity comes from a SHA-256 digest of the *complete* id, and the sanitized prefix is
 * decoration for anyone reading `adb shell ls`. Collisions now require breaking SHA-256 rather
 * than a few seconds of search.
 */
internal fun String.toFileName(): String {
    val readable = take(MAX_READABLE_LENGTH)
        .map { if (it.isLetterOrDigit() || it == '-') it else '_' }
        .joinToString("")
    return "$readable-${sha256Hex().take(DIGEST_LENGTH)}"
}

private fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** Readable prefix only; identity lives in the digest, so truncating this is harmless. */
private const val MAX_READABLE_LENGTH = 32

/**
 * 128 bits of digest.
 *
 * Well past what a collision search could reach, and short enough to keep the filename readable
 * alongside the prefix.
 */
private const val DIGEST_LENGTH = 32
