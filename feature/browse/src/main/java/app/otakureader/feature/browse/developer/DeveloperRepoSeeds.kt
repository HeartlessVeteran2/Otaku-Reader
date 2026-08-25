package app.otakureader.feature.browse.developer

import android.content.Context
import app.otakureader.core.common.dispatchers.Dispatcher
import app.otakureader.core.common.dispatchers.OtakuReaderDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Reads the developer's own extension-repository list out of an optional assets file.
 *
 * ## Why an assets file rather than a Kotlin constant
 *
 * The list is personal, and this repository is public. Hardcoding it in source would publish it to
 * everyone reading the project on GitHub, which is the opposite of what a hidden screen is for —
 * the screen would be hidden while the thing it reveals sat in plain sight in the diff.
 *
 * So [ASSET_NAME] is listed in `.gitignore`. Drop your list in before building and your APK has it
 * ingrained; the file never enters git, and a clone by anyone else builds an app whose developer
 * screen simply has nothing to seed. `dev-repos.txt.example` is committed as documentation of the
 * format.
 *
 * ## Format
 *
 * One URL per line. Blank lines are skipped, and `#` starts a comment — both so the file can be
 * annotated and entries can be commented out without deleting them.
 *
 * Every line is validated as an http/https URL before it is offered, for the same reason
 * `ExtensionRepositoriesViewModel` validates typed input: a malformed entry that reaches
 * `addRepository` becomes a stored row that fails on every refresh afterwards, and the file it
 * came from is not on screen to correct.
 */
@Singleton
class DeveloperRepoSeeds @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(OtakuReaderDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * The configured URLs, or an empty list when the file is absent.
     *
     * Absent is the normal case, not an error: a public build has no such file. It is reported as
     * an empty list rather than a failure so the developer screen renders its "nothing configured"
     * state instead of an error the user can do nothing about.
     */
    suspend fun load(): List<String> = withContext(ioDispatcher) {
        val raw = try {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            return@withContext emptyList()
        }

        raw.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .filter(::isUsableUrl)
            .distinct()
            .toList()
    }

    /**
     * Whether [candidate] is an http/https URL with a host.
     *
     * Uses `java.net.URI` rather than OkHttp's `toHttpUrlOrNull`, which would be the more natural
     * choice but is not available here: `core:extension` declares OkHttp as `implementation`, so
     * it is absent from this module's compile classpath.
     *
     * The scheme check is the part that matters. `URI` happily parses `example.com/repo` as a
     * relative reference with a null scheme and null host, so accepting anything that merely
     * parses would let a bare hostname through — and it would then fail on every refresh, from a
     * file the user cannot see from inside the app.
     */
    private fun isUsableUrl(candidate: String): Boolean = try {
        val uri = URI(candidate)
        uri.scheme?.lowercase() in ALLOWED_SCHEMES && !uri.host.isNullOrBlank()
    } catch (_: URISyntaxException) {
        false
    }

    companion object {
        const val ASSET_NAME = "dev-repos.txt"

        private val ALLOWED_SCHEMES = setOf("http", "https")
    }
}
