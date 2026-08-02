package app.otakureader.core.js.store

import android.content.Context
import app.otakureader.core.js.protocol.JsProtocol
import app.otakureader.core.js.protocol.JsSourceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** An installed JavaScript source: its manifest and the script itself. */
data class InstalledJsSource(
    val config: JsSourceConfig,
    val script: String,
)

/**
 * On-disk storage for installed JavaScript sources.
 *
 * Each source is two files under `filesDir/js-exts/`: `<id>.js` holding the script and
 * `<id>.json` holding its [JsSourceConfig]. Installing is a file write — there is no APK, no
 * `PackageManager`, no signature dance and no system install prompt, which is the entire reason
 * this backend exists.
 *
 * Both files are written before either is considered installed, so a crash mid-install leaves a
 * source that [installed] skips rather than one that loads with a missing script.
 */
@Singleton
class JsSourceStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    internal companion object {
        const val DIRECTORY = "js-exts"
        const val SCRIPT_EXTENSION = "js"
        const val CONFIG_EXTENSION = "json"
    }

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * Every installed source, skipping any that cannot be read.
     *
     * A source whose manifest is corrupt is dropped rather than thrown, because one bad file
     * must not take out the whole source list — that failure mode is precisely what made the APK
     * loader undiagnosable, where a single bad extension left Browse showing nothing.
     */
    suspend fun installed(): List<InstalledJsSource> = withContext(Dispatchers.IO) {
        directory.listFiles { file -> file.extension == CONFIG_EXTENSION }
            .orEmpty()
            .mapNotNull { configFile ->
                runCatching {
                    val config = JsProtocol.json.decodeFromString<JsSourceConfig>(configFile.readText())
                    val script = File(directory, "${config.id.toFileName()}.$SCRIPT_EXTENSION")
                    if (!script.exists()) return@runCatching null
                    InstalledJsSource(config, script.readText())
                }.getOrNull()
            }
    }

    suspend fun install(config: JsSourceConfig, script: String) = withContext(Dispatchers.IO) {
        val name = config.id.toFileName()
        // Script first: [installed] keys off the manifest and requires the script to exist, so
        // writing the manifest last means a half-finished install is simply invisible.
        File(directory, "$name.$SCRIPT_EXTENSION").writeText(script)
        File(directory, "$name.$CONFIG_EXTENSION").writeText(JsProtocol.json.encodeToString(config))
    }

    suspend fun uninstall(sourceId: String) = withContext(Dispatchers.IO) {
        val name = sourceId.toFileName()
        File(directory, "$name.$SCRIPT_EXTENSION").delete()
        File(directory, "$name.$CONFIG_EXTENSION").delete()
        Unit
    }
}

/**
 * Map a source id onto a safe filename.
 *
 * Source ids arrive from a remote index that the user does not control, and they are used here
 * to build paths. Interpolating one straight into a `File` makes it a path-traversal primitive:
 * an id of `../../databases/otaku` would resolve outside the extension directory and let an
 * install overwrite the app's own database. Ids are also used as map keys and DataStore keys, so
 * this has to be deterministic rather than random.
 *
 * Every character outside a conservative allow-list becomes `_`, and the result is hashed-
 * suffixed so two ids that differ only in stripped characters cannot collide onto one file and
 * silently overwrite each other.
 */
internal fun String.toFileName(): String {
    val sanitized = map { if (it.isLetterOrDigit() || it == '-') it else '_' }.joinToString("")
    return "${sanitized.take(MAX_NAME_LENGTH)}-${hashCode().toUInt().toString(HASH_RADIX)}"
}

/** Keeps the readable prefix short enough that the hash suffix always survives truncation. */
private const val MAX_NAME_LENGTH = 48
private const val HASH_RADIX = 16
