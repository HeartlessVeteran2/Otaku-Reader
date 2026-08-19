package app.otakureader.core.js.remote

import app.otakureader.core.extension.domain.backend.JsExtensionFetch
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders whatever JSON primitive appears in a field as its string form.
 *
 * The published indexes disagree on the type of two fields that matter. Mangayomi's writes `id`
 * and `itemType` as **numbers**; the Sora-style indexes write them as **strings**. Both are
 * legitimate and neither is going to change, so the reader has to accept either.
 *
 * Done with a serializer rather than by switching the parser to `isLenient` because leniency is
 * global: it would also start accepting unquoted keys and values everywhere else in the document,
 * which is a much larger promise to make about a file fetched from an arbitrary remote host. This
 * widens exactly the two fields that need widening.
 */
private object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        // `content` is the primitive's text regardless of whether it was quoted, so a numeric id
        // arrives as its decimal — which is stable, and is what the string-typed indexes publish
        // for the same source anyway.
        return (element as? JsonPrimitive)?.content.orEmpty()
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/**
 * One entry in a repository's JavaScript source index.
 *
 * Mirrors the Mangayomi/Sora index shape, which is what the existing community JavaScript sources
 * are published against.
 *
 * **This claim was previously wider than the code.** The field *names* matched, but three of the
 * types did not, and each failure was silent in a different way against the real Mangayomi index:
 * `id` and `itemType` are numbers there, so every entry either failed to decode or survived
 * decoding and was then dropped by an `itemType == "manga"` comparison that a numeric `0` can
 * never satisfy; and nothing read `sourceCodeLanguage`, so the 249 Dart entries that share the
 * index with the 114 JavaScript ones would have been downloaded and handed to the JS engine.
 */
@Serializable
internal data class JsExtensionDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String,
    val name: String,
    val baseUrl: String,
    val lang: String,
    /**
     * The source's API host, when it keeps one separate from [baseUrl].
     *
     * Blank for scraping sources, which is most of them, and set for roughly a quarter of the
     * index. Sources that set it build nearly every request from it, so dropping the field on the
     * floor makes those sources fail as ordinary HTTP errors against `undefined/...`.
     */
    val apiUrl: String = "",
    /** Where the source code lives. Relative paths resolve against the repository base. */
    val sourceCodeUrl: String = "",
    val version: String = "1.0.0",
    /**
     * Monotonic build number.
     *
     * Absent from every entry of the Mangayomi index — use [effectiveVersionCode], never this
     * field directly. Kept because the Sora-style indexes do publish it.
     */
    val versionCode: Int = 0,
    val iconUrl: String? = null,
    val isNsfw: Boolean = false,
    val hasCloudflare: Boolean = false,
    /**
     * Which language the source is written in: `0` Dart, `1` JavaScript.
     *
     * Defaults to JavaScript because a repository that serves *only* JavaScript omits the field
     * entirely — that is the shape this reader was originally written against — and defaulting to
     * Dart would silently empty every such repository.
     */
    val sourceCodeLanguage: Int = LANGUAGE_JAVASCRIPT,
    /**
     * `manga`, `novel` or `anime` — as a name in some indexes, as an ordinal in others.
     *
     * Only manga entries are surfaced today; see the filter in `fetchIndex`. Absent means manga,
     * which is what the overwhelming majority of index entries omit it for.
     */
    @Serializable(with = FlexibleStringSerializer::class)
    val itemType: String = ITEM_TYPE_MANGA,
) {
    /** True when this entry is a manga source, under either spelling of the field. */
    val isManga: Boolean
        get() = itemType.equals(ITEM_TYPE_MANGA, ignoreCase = true) ||
            itemType == ITEM_TYPE_MANGA_ORDINAL

    val isJavaScript: Boolean
        get() = sourceCodeLanguage == LANGUAGE_JAVASCRIPT

    /**
     * A comparable build number, derived from [version] when the index omits [versionCode].
     *
     * Update detection and the duplicate-entry tiebreak both order by this. With the raw field the
     * Mangayomi index pins every source at the same number, which does not fail loudly — it just
     * means an installed source is never seen to have a newer release, and the user quietly stays
     * on whatever version they first installed forever.
     *
     * Segments are packed rather than summed so that ordering follows precedence: 1.0.0 must beat
     * 0.9.9, which a sum gets wrong.
     *
     * The radix is 1000, not 100, and the arithmetic runs in `Long`. A two-digit radix looks
     * sufficient against today's published versions but fails quietly the first time a segment
     * reaches 100: 0.100.0 and 0.99.0 would clamp to the same number, so a real update would never
     * be offered. Saturating the final value rather than each segment keeps ordering monotonic all
     * the way up to the clamp instead of flattening at every position.
     */
    val effectiveVersionCode: Int
        get() {
            if (versionCode > 0) return versionCode
            val parts = version.split('.', '-', '+')
            var packed = 0L
            for (index in 0 until VERSION_SEGMENTS) {
                val segment = parts.getOrNull(index)?.takeWhile(Char::isDigit)?.toLongOrNull() ?: 0L
                packed = packed * VERSION_SEGMENT_RADIX + segment.coerceAtMost(VERSION_SEGMENT_RADIX - 1)
            }
            return packed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    internal companion object {
        const val ITEM_TYPE_MANGA = "manga"

        /** Mangayomi writes the item type as an ordinal, where manga is 0. */
        const val ITEM_TYPE_MANGA_ORDINAL = "0"

        const val LANGUAGE_JAVASCRIPT = 1

        private const val VERSION_SEGMENTS = 3
        private const val VERSION_SEGMENT_RADIX = 1000L
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
    httpClient: OkHttpClient,
) {

    /**
     * The shared client, with cross-scheme redirects switched off.
     *
     * Checking the scheme of the URL we *ask* for is not enough on its own. OkHttp follows
     * redirects by default, and `followSslRedirects` defaults to true — so an `https://` script
     * URL that 302s to `http://` would be fetched in plaintext, and anyone on the network path
     * could replace the JavaScript that is about to be executed. The scheme check would have
     * passed while the guarantee it stands for was gone.
     *
     * `followSslRedirects(false)` makes OkHttp refuse to follow a redirect that changes scheme in
     * either direction; the 3xx comes back as-is and the `isSuccessful` check below rejects it.
     * Same-scheme redirects still work, which matters because repositories routinely serve
     * artifacts from a CDN on another host.
     *
     * Doing it this way rather than disabling redirects and walking them by hand is deliberate.
     * `JsHttpBridge` took the manual route and silently lost the rules OkHttp applies for free —
     * stripping `Authorization` across an origin change, turning 301/302/303 POSTs into bodyless
     * GETs. One builder flag keeps `RetryAndFollowUpInterceptor`'s semantics intact.
     *
     * Derived from the injected client rather than built fresh, so certificate pinning, the
     * cookie jar, rate limiting and the connection pool all still apply.
     */
    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .followSslRedirects(false)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    internal companion object {
        /**
         * The dedicated JavaScript index.
         *
         * A repository serving this path means JavaScript unambiguously, so anything that goes
         * wrong after a successful fetch — a malformed document, a truncated body — is a real
         * fault worth reporting to the user.
         */
        const val DEDICATED_INDEX_PATH = "/js/index.json"

        /**
         * The combined index, tried only when [DEDICATED_INDEX_PATH] is not served.
         *
         * The Mangayomi repository — the ecosystem this backend exists to consume — publishes one
         * index here covering both its Dart and its JavaScript sources, and serves nothing at the
         * dedicated path. Without this fallback the single largest supplier of JavaScript sources
         * reports itself as a repository with no sources.
         *
         * **Failures here are swallowed, unlike the dedicated path.** This is the same filename
         * the APK backend reads, so a Keiyoushi-style repository answers it with an index of a
         * completely different shape. That is not a fault: it is the ordinary, expected answer
         * from a repository that serves only APKs, and reporting it would put an error in front
         * of every user about a backend they are using correctly.
         */
        const val COMBINED_INDEX_PATH = "/index.json"

        /**
         * Ceiling on a downloaded script.
         *
         * A source is a few tens of KB of JavaScript. The cap exists so a repository — which is
         * an arbitrary remote host — cannot make the app read an unbounded body into memory by
         * pointing `sourceCodeUrl` at something enormous.
         */
        const val MAX_SCRIPT_BYTES = 2L * 1024 * 1024

        /**
         * Ceiling on a repository index.
         *
         * Larger than the script cap because one index describes every source a repository
         * offers — Keiyoushi's APK equivalent is around a megabyte. Still bounded, for the same
         * reason: the host is arbitrary and `string()` would buffer whatever it returned.
         */
        const val MAX_INDEX_BYTES = 8L * 1024 * 1024

        /** Only https:// is accepted, matching the guard `ExtensionInstaller` applies to APKs. */
        const val HTTPS_PREFIX = "https://"

        const val HTTP_NOT_FOUND = 404
    }

    /**
     * Every JavaScript source offered by [repoUrls].
     *
     * Failures are isolated per repository, matching the APK path: one unreachable or malformed
     * repository must not empty the list contributed by the others. A repository with no
     * JavaScript index is the common case, not a failure, so it contributes nothing and is not
     * reported.
     */
    suspend fun fetchAvailable(repoUrls: List<String>): JsExtensionFetch = withContext(Dispatchers.IO) {
        var servedAnyIndex = false
        var firstFailure: Throwable? = null

        val extensions = repoUrls.flatMap { rawUrl ->
            val baseUrl = rawUrl.trimEnd('/')
            runCatching { fetchIndex(baseUrl) }
                // Only a repository that actually served a JavaScript index counts. An APK-only
                // repository succeeds here with an empty result, and flagging that as "served"
                // would let one such repository mask every other repository's genuine failure.
                .onSuccess { if (it.servedJsIndex) servedAnyIndex = true }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    android.util.Log.w("JsExtensionRemoteDS", "No JS index at $baseUrl: ${error.message}")
                    // Kept, not just logged. A malformed index on a JavaScript-only repository
                    // would otherwise surface as its APK endpoint's 404 — an error about a
                    // backend the user is not using and cannot act on.
                    if (firstFailure == null) firstFailure = error
                }
                .getOrNull()?.extensions.orEmpty()
        }

        JsExtensionFetch(
            extensions = extensions
                // Two repositories can offer the same source; keep the newer build rather than
                // whichever happened to be fetched last.
                .groupBy { it.pkgName }
                .values
                .map { candidates -> candidates.maxByOrNull { it.versionCode } ?: candidates.first() },
            servedAnyIndex = servedAnyIndex,
            firstFailure = firstFailure,
        )
    }

    /**
     * The repository's JavaScript sources, and whether it actually served a JavaScript index.
     *
     * Both halves matter and they are not interchangeable. `JsExtensionBackend` already documents
     * why `servedAnyIndex` is carried rather than inferred from `extensions.isEmpty()`: a
     * repository that served an index listing no manga sources and one that served nothing are
     * different answers, and `ExtensionRemoteDataSource` uses the distinction to decide whether a
     * round of failures is worth reporting. Returning a bare empty list for an APK-only
     * repository collapsed them, which would report a failed refresh as a successful empty one.
     *
     * The paths are handled asymmetrically on purpose — see the notes on the constants. Precisely:
     *
     * - dedicated path **absent** (404) → fall through to the combined path, silently;
     * - dedicated path **broken** (any other transport or body failure) → throws, because a
     *   repository publishing that path has declared itself a JavaScript repository and its index
     *   being unreachable is a real fault that the combined endpoint must not paper over;
     * - dedicated path served but **unreadable** → throws, same reasoning;
     * - combined path **absent** (404) → empty and not counted as served. A repository answering
     *   neither path is almost always an APK-only one publishing `index.min.json` and nothing
     *   else, which is not a fault and must not be reported;
     * - combined path **broken** (any other failure) → throws;
     * - combined path served but **unreadable** → empty *and not counted as served*, because that
     *   filename is shared with the APK backend and a foreign index is the expected answer from an
     *   APK-only repository.
     */
    private fun fetchIndex(baseUrl: String): IndexResult {
        // Null only for a genuine 404. Anything else — a 500, a timeout, an oversized body —
        // propagates, so a broken JavaScript index cannot be hidden behind the combined endpoint.
        val dedicated = fetchIndexBodyOrNull(baseUrl + DEDICATED_INDEX_PATH)

        // Served, so its content is authoritative and this parse is allowed to throw.
        if (dedicated != null) return IndexResult(parseIndex(dedicated, baseUrl), servedJsIndex = true)

        // A 404 here too is an answer, not a fault: the APK backend documents `index.min.json` as
        // the common third-party format with `index.json` only as its fallback, so a repository
        // serving min-only answers neither path this reader asks for. Reporting that would put an
        // error in front of every user with such a repository configured. Any other failure — a
        // 500, a timeout, an oversized body — still propagates.
        val combined = fetchIndexBodyOrNull(baseUrl + COMBINED_INDEX_PATH)
            ?: return IndexResult(emptyList(), servedJsIndex = false)

        return try {
            IndexResult(parseIndex(combined, baseUrl), servedJsIndex = true)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Fetched but unreadable: the ordinary answer from an APK-only repository. Not a
            // fault, and deliberately not counted as having served a JavaScript index.
            IndexResult(emptyList(), servedJsIndex = false)
        }
    }

    /** The body, or null when the path is genuinely absent. Every other failure propagates. */
    private fun fetchIndexBodyOrNull(indexUrl: String): String? =
        try {
            fetchIndexBody(indexUrl)
        } catch (e: JsExtensionNotFoundException) {
            null
        }

    private fun fetchIndexBody(indexUrl: String): String {
        requireHttps(indexUrl)

        return httpClient.newCall(Request.Builder().url(indexUrl).build()).execute().use { response ->
            // 404 is the one status that means "this repository does not offer this path", which
            // is an answer rather than a fault. Every other status is a fault.
            if (response.code == HTTP_NOT_FOUND) {
                throw JsExtensionNotFoundException("No index at $indexUrl")
            }
            if (!response.isSuccessful) {
                throw JsExtensionFetchException("HTTP ${response.code} fetching $indexUrl")
            }
            val responseBody = response.body
                ?: throw JsExtensionFetchException("Empty index body from $indexUrl")
            // Bounded like the script read. A repository is an arbitrary remote host, and
            // `string()` buffers whatever it sends — so an index alone could exhaust memory
            // during source discovery, before a single script had been downloaded.
            responseBody.readBounded(MAX_INDEX_BYTES, "Index at $indexUrl")
        }
    }

    private fun parseIndex(body: String, baseUrl: String): List<Extension> {
        return json.decodeFromString<List<JsExtensionDto>>(body)
            // Only manga sources are surfaced. `JsSource` implements the manga contract, so a
            // novel or anime entry would install cleanly and then fail on every read — an entry
            // the user can add but cannot use, which is worse than one that never appeared.
            //
            // Filtering here rather than carrying the type through and blocking installation
            // later is the smaller, more honest change: there is no half-working state to
            // explain, and nothing is lost, because these sources could not work anyway. Stage 7
            // adds the novel runtime and relaxes this filter in the same change that makes the
            // entries usable.
            .filter { it.isManga }
            // Only JavaScript. The Mangayomi index lists Dart and JavaScript sources together —
            // Dart is in fact the majority — and a Dart file handed to QuickJS does not fail at
            // install time. It downloads, stores and registers exactly like a working source, and
            // then throws a syntax error on the user's first browse, naming the script rather
            // than the reason it could never have run.
            //
            // This does mean reading a field to decide what an entry is, which the note on the
            // index paths argues against for whole *indexes*. The reasoning does not carry over:
            // that argument is about guessing which backend a file belongs to, and this is the
            // index's own explicit, documented discriminator for its own entries.
            .filter { it.isJavaScript }
            // A blank URL is an entry there is nothing to download for, and a blank id is one
            // that cannot own its stored preferences or be uninstalled by name. Both reach here
            // when a repository serves an index of a different shape whose other fields happen to
            // overlap, which decodes into mostly-default DTOs rather than failing outright — and
            // when `id` arrives as an object or array, which the flexible serializer renders as
            // empty rather than throwing.
            //
            // Dropped here rather than rejected in the serializer on purpose: throwing mid-decode
            // fails the entire document, so one malformed entry would cost the user every source
            // that repository offers.
            .filter { it.sourceCodeUrl.isNotBlank() && it.id.isNotBlank() }
            .map { it.toDomain(baseUrl) }
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
            body.readBounded(MAX_SCRIPT_BYTES, "Script at $scriptUrl")
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
 * The repository does not offer this path at all — a 404, not a fault.
 *
 * A distinct type rather than a status check at the call site, so that "absent" and "broken" can
 * never be collapsed by a `catch (Exception)` that was only meant to handle the first.
 */
class JsExtensionNotFoundException(message: String) : RuntimeException(message)

/**
 * What one repository yielded, and whether it served a JavaScript index at all.
 *
 * The flag is not derivable from `extensions.isEmpty()` — an index listing no manga sources and a
 * foreign index this reader cannot read both produce an empty list and mean opposite things.
 */
private data class IndexResult(
    val extensions: List<Extension>,
    val servedJsIndex: Boolean,
)

/**
 * Read a body, refusing it if it exceeds [limit].
 *
 * Reads one byte past the cap so an oversized body is *detected* rather than silently truncated.
 * Truncation is the failure worth preventing in both places this is used: a half-downloaded
 * script installs as if whole and fails somewhere inside the engine, and a half-read index
 * either fails to parse or — worse — parses as a shorter list, quietly hiding sources.
 */
private fun ResponseBody.readBounded(limit: Long, what: String): String {
    val bytes = source().apply { request(limit + 1) }.buffer.snapshot()
    if (bytes.size > limit) {
        throw JsExtensionFetchException("$what exceeds ${limit / 1024} KiB")
    }
    return bytes.utf8()
}

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
    // Derived, not the raw field — the Mangayomi index publishes no versionCode at all, and the
    // raw default would make every source look permanently up to date.
    versionCode = effectiveVersionCode,
    versionName = version,
    sources = listOf(
        ExtensionSource(
            id = id.toStableId(),
            name = name,
            lang = lang,
            // The site the source scrapes — NOT the repository it was listed in.
            baseUrl = baseUrl,
            apiUrl = apiUrl,
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
