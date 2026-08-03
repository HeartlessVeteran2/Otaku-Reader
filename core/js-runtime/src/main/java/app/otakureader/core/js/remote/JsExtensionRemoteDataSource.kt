package app.otakureader.core.js.remote

import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One entry in a repository's JavaScript source index.
 *
 * Mirrors the Mangayomi/Sora index shape, which is what the existing community JavaScript
 * sources are published against. Following it rather than inventing a format means those sources
 * work here unmodified — the same reasoning that keeps the Tachiyomi APK contract intact.
 */
@Serializable
internal data class JsExtensionDto(
    val id: String,
    val name: String,
    val baseUrl: String,
    val lang: String,
    /** Where the `.js` itself lives. Relative paths resolve against the repository base. */
    val sourceCodeUrl: String,
    val version: String = "1.0.0",
    /** Monotonic build number, used for update detection. */
    val versionCode: Int = 1,
    val iconUrl: String? = null,
    val isNsfw: Boolean = false,
    val hasCloudflare: Boolean = false,
    /** `manga` or `novel`. Novels become readable in Stage 7; they list correctly from now. */
    val itemType: String = ITEM_TYPE_MANGA,
) {
    internal companion object {
        const val ITEM_TYPE_MANGA = "manga"
    }
}

/**
 * Fetches the JavaScript source index and the scripts themselves.
 *
 * ### Why a separate path under the same repository URL
 *
 * JavaScript sources are read from `<repo>/js/index.json`, alongside the APK backend's
 * `<repo>/index.min.json`. Sharing the configured repository list means the user adds a URL once
 * and gets whichever backends that repository actually serves, with no second settings screen and
 * no edits to the repository UI.
 *
 * A distinct filename rather than content-sniffing a shared one is deliberate. Deciding which
 * backend an index belongs to by inspecting its fields would misclassify the moment the two
 * formats grew a field in common, and a misclassified index fails as "this repository has no
 * sources" — silent, and indistinguishable from an empty repository. A repository that serves
 * only APKs simply 404s here, which is an unambiguous answer.
 */
@Singleton
class JsExtensionRemoteDataSource @Inject constructor(
    private val httpClient: OkHttpClient,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    internal companion object {
        const val INDEX_PATH = "/js/index.json"

        /**
         * Ceiling on a downloaded script.
         *
         * A source is a few tens of KB of JavaScript. The cap exists so a repository — which is
         * an arbitrary remote host — cannot make the app read an unbounded body into memory by
         * pointing `sourceCodeUrl` at something enormous.
         */
        const val MAX_SCRIPT_BYTES = 2L * 1024 * 1024

        /** Only https:// is accepted, matching the guard `ExtensionInstaller` applies to APKs. */
        const val HTTPS_PREFIX = "https://"
    }

    /**
     * Every JavaScript source offered by [repoUrls].
     *
     * Failures are isolated per repository, matching the APK path: one unreachable or malformed
     * repository must not empty the list contributed by the others. A repository with no
     * JavaScript index is the common case, not a failure, so it contributes nothing and is not
     * reported.
     */
    suspend fun fetchAvailable(repoUrls: List<String>): List<Extension> = withContext(Dispatchers.IO) {
        repoUrls.flatMap { rawUrl ->
            val baseUrl = rawUrl.trimEnd('/')
            runCatching { fetchIndex(baseUrl) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    android.util.Log.w("JsExtensionRemoteDS", "No JS index at $baseUrl: ${error.message}")
                }
                .getOrDefault(emptyList())
        }
            // Two repositories can offer the same source; keep the newer build rather than
            // whichever happened to be fetched last.
            .groupBy { it.pkgName }
            .values
            .map { candidates -> candidates.maxByOrNull { it.versionCode } ?: candidates.first() }
    }

    private fun fetchIndex(baseUrl: String): List<Extension> {
        val indexUrl = baseUrl + INDEX_PATH
        requireHttps(indexUrl)

        val body = httpClient.newCall(Request.Builder().url(indexUrl).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw JsExtensionFetchException("HTTP ${response.code} fetching $indexUrl")
            }
            response.body?.string() ?: throw JsExtensionFetchException("Empty index body from $indexUrl")
        }

        return json.decodeFromString<List<JsExtensionDto>>(body).map { it.toDomain(baseUrl) }
    }

    /**
     * Download a source's script.
     *
     * Reads at the cap plus one byte so an oversized script is *detected* rather than silently
     * truncated. A half-downloaded script would be installed as if whole and then fail somewhere
     * inside the engine, which is a far harder failure to trace back to its cause than a refusal
     * at download time.
     */
    suspend fun downloadScript(scriptUrl: String): String = withContext(Dispatchers.IO) {
        requireHttps(scriptUrl)

        httpClient.newCall(Request.Builder().url(scriptUrl).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw JsExtensionFetchException("HTTP ${response.code} downloading $scriptUrl")
            }
            val body = response.body ?: throw JsExtensionFetchException("Empty script body from $scriptUrl")
            val bytes = body.source().apply { request(MAX_SCRIPT_BYTES + 1) }.buffer.snapshot()
            if (bytes.size > MAX_SCRIPT_BYTES) {
                throw JsExtensionFetchException(
                    "Script at $scriptUrl exceeds ${MAX_SCRIPT_BYTES / 1024} KiB"
                )
            }
            bytes.utf8()
        }
    }

    /**
     * Reject anything that is not HTTPS.
     *
     * A JavaScript source carries no signature — there is nothing to verify it against once it
     * arrives, unlike an APK. Transport security is therefore the *only* control on this path,
     * which makes it stricter here than it is for APKs, not looser: a plaintext fetch would let
     * anyone on the network path substitute the script that is about to be executed.
     */
    private fun requireHttps(url: String) {
        if (!url.startsWith(HTTPS_PREFIX)) {
            throw SecurityException("JavaScript sources must be served over HTTPS. Rejected: $url")
        }
    }
}

/** Thrown when a repository index or script cannot be fetched or parsed. */
class JsExtensionFetchException(message: String) : RuntimeException(message)

/**
 * Map an index entry onto the domain model.
 *
 * **Two different URLs are in play and they must not be confused.** [repoUrl] is where the index
 * was fetched from; `this.baseUrl` is the manga site the source actually scrapes. The parameter
 * is named `repoUrl` rather than `baseUrl` precisely so it cannot shadow the DTO's field — an
 * earlier version took `baseUrl` and silently handed the *repository* URL to every source, which
 * would have pointed every relative request and every synthesised Referer at the index host
 * instead of the site. Nothing would have crashed; sources would simply have returned nothing.
 */
private fun JsExtensionDto.toDomain(repoUrl: String): Extension = Extension(
    id = id.toStableId(),
    pkgName = id,
    name = name,
    versionCode = versionCode,
    versionName = version,
    sources = listOf(
        ExtensionSource(
            id = id.toStableId(),
            name = name,
            lang = lang,
            // The site the source scrapes — NOT the repository it was listed in.
            baseUrl = baseUrl,
        )
    ),
    status = InstallStatus.AVAILABLE,
    apkPath = null,
    // The script, on the other hand, really does live on the repository host.
    apkUrl = resolve(repoUrl, sourceCodeUrl),
    iconUrl = iconUrl?.let { resolve(repoUrl, it) },
    lang = lang,
    isNsfw = isNsfw,
    installDate = null,
    // No signature exists for a script. Null is the honest value; a placeholder would make
    // `isTrusted` report a verification that never happened.
    signatureHash = null,
    isShared = false,
    repoUrl = repoUrl,
    hasCloudflare = hasCloudflare,
    isJavaScript = true,
)

/**
 * A stable 63-bit id derived from the source id.
 *
 * `String.hashCode()` is 32 bits, so a library of a few thousand sources has a real chance of a
 * birthday collision — and a collision here means two different sources sharing a database
 * primary key, where one silently replaces the other. This takes the digest's leading eight
 * bytes and clears the sign bit, giving 63 usable bits, which makes a collision vanishingly
 * unlikely. The sign bit goes because a negative id reads as an error elsewhere in the app.
 */
private fun String.toStableId(): Long {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    var value = 0L
    repeat(Long.SIZE_BYTES) { index -> value = (value shl Byte.SIZE_BITS) or (digest[index].toLong() and 0xFF) }
    return value and Long.MAX_VALUE
}

/** Resolve a possibly-relative index URL against the repository base. */
private fun resolve(baseUrl: String, path: String): String =
    if (path.startsWith("https://") || path.startsWith("http://")) path else "$baseUrl/${path.trimStart('/')}"
