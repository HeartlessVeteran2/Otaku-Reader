package app.otakureader.core.extension.data.remote

import app.otakureader.core.extension.domain.backend.JsExtensionBackend
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import app.otakureader.core.extension.domain.repository.ExtensionRepoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.otakureader.core.common.net.await
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * DTOs for extension repository API responses.
 * This represents the JSON structure returned by the extension repo server.
 */

/**
 * Standard index.json format with wrapped extensions array.
 */
@Serializable
data class ExtensionRepoResponse(
    @SerialName("extensions")
    val extensions: List<ExtensionDto>,

    @SerialName("last_modified")
    val lastModified: Long,
)

/**
 * Standard extension format (used in index.json).
 */
@Serializable
data class ExtensionDto(
    @SerialName("id")
    val id: Long,

    @SerialName("pkg_name")
    val pkgName: String,

    @SerialName("name")
    val name: String,

    @SerialName("version_code")
    val versionCode: Int,

    @SerialName("version_name")
    val versionName: String,

    @SerialName("sources")
    val sources: List<ExtensionSourceDto>,

    @SerialName("apk_url")
    val apkUrl: String? = null,

    /**
     * Some repos serve standard-format index.json but use the minified field name `apk`
     * (a bare filename) instead of `apk_url`. Accept both; [toDomain] resolves whichever
     * is present against the repo base URL.
     */
    @SerialName("apk")
    val apk: String? = null,

    @SerialName("icon_url")
    val iconUrl: String? = null,

    @SerialName("lang")
    val lang: String,

    @SerialName("is_nsfw")
    val isNsfw: Boolean = false,

    @SerialName("signature")
    val signature: String? = null,
)

@Serializable
data class ExtensionSourceDto(
    @SerialName("id")
    val id: Long,

    @SerialName("name")
    val name: String,

    @SerialName("lang")
    val lang: String,

    @SerialName("base_url")
    val baseUrl: String,

    @SerialName("supports_search")
    val supportsSearch: Boolean = true,

    @SerialName("supports_latest")
    val supportsLatest: Boolean = true,
)

/**
 * Minified extension format (used in index.min.json from Keiyoushi, Komikku, Suwayomi).
 * This format is more compact and uses shorter field names.
 */
@Serializable
data class MinifiedExtensionDto(
    @SerialName("name")
    val name: String,

    @SerialName("pkg")
    val pkg: String,

    @SerialName("apk")
    val apk: String,

    @SerialName("lang")
    val lang: String,

    @SerialName("code")
    val code: Int,

    @SerialName("version")
    val version: String,

    @SerialName("nsfw")
    val nsfw: Int = 0,

    @SerialName("sources")
    val sources: List<MinifiedExtensionSourceDto>,

    @SerialName("hasReadme")
    val hasReadme: Boolean = false,

    @SerialName("hasChangelog")
    val hasChangelog: Boolean = false,

    @SerialName("icon")
    val icon: String? = null,
)

@Serializable
data class MinifiedExtensionSourceDto(
    @SerialName("name")
    val name: String,

    @SerialName("lang")
    val lang: String,

    @SerialName("id")
    val id: String,

    @SerialName("baseUrl")
    val baseUrl: String,

    @SerialName("versionId")
    val versionId: Int = 0,

    @SerialName("hasCloudflare")
    val hasCloudflare: Int = 0,
)

/**
 * Remote data source for fetching extension information and APKs.
 */
interface ExtensionRemoteDataSource {

    /**
     * Fetch list of available extensions from the repository.
     */
    suspend fun fetchAvailableExtensions(): Result<List<Extension>>

    /**
     * Download an extension APK to the specified destination.
     */
    suspend fun downloadApk(apkUrl: String, destination: File): Result<File>
}

class ExtensionRemoteDataSourceImpl(
    private val repoRepository: ExtensionRepoRepository,
    private val httpClient: OkHttpClient = createDefaultClient(),
    /**
     * The JavaScript backend, when one is wired in.
     *
     * The merge happens here rather than a layer up because this class already resolves and
     * normalises the configured repository list. Doing it elsewhere would mean two readers of
     * that list and two chances to normalise a URL differently — so a repository could resolve
     * one way for APKs and another for scripts, which fails as "this repository has no sources"
     * rather than as anything a user could act on.
     *
     * Null leaves the APK behaviour exactly as it was, which is what the existing tests exercise.
     */
    private val jsBackend: JsExtensionBackend? = null,
) : ExtensionRemoteDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val REPO_INDEX_PATH = "/index.json"
        private const val REPO_INDEX_MIN_PATH = "/index.min.json"

        /** Strip trailing index.json or index.min.json if the user pasted the full URL. */
        fun normalizeRepoUrl(url: String): String {
            return url.trimEnd('/')
                .removeSuffix(REPO_INDEX_PATH)
                .removeSuffix(REPO_INDEX_MIN_PATH)
                .trimEnd('/')
        }

        fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun fetchAvailableExtensions(): Result<List<Extension>> {
        return withContext(Dispatchers.IO) {
            try {
                val repositories = repoRepository.getRepositories().first()
                if (repositories.isEmpty()) return@withContext Result.success(emptyList())

                val extensions = mutableListOf<Extension>()
                val failures = mutableListOf<Pair<String, Exception>>()
                var successCount = 0

                // Isolate failures per repository: one unreachable or malformed repo must
                // not wipe out the extension list from every other configured repo.
                repositories.forEach { rawUrl ->
                    val baseUrl = normalizeRepoUrl(rawUrl)
                    try {
                        val repoExtensions = fetchFromRepository(baseUrl)
                        extensions.addAll(repoExtensions.map { it.copy(repoUrl = baseUrl) })
                        successCount++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "ExtensionRemoteDS",
                            "Failed to fetch extensions from $baseUrl: ${e.message}"
                        )
                        failures.add(baseUrl to e)
                    }
                }

                // The JavaScript backend reads the same repository list. Its failures are
                // absorbed the same way a single repository's are: a repository that serves no
                // JavaScript index is the common case, not an error.
                //
                // Crucially this runs even when every APK fetch failed, and its results count
                // towards success below. The inverse — letting an APK failure return early —
                // is the exact defect that shipped in Stage 4a, where one thrown exception on
                // the APK path silently dropped every JavaScript source. Isolation has to run
                // in both directions or it is not isolation.
                val jsFetch = jsBackend?.let { backend ->
                    val normalized = repositories.map { normalizeRepoUrl(it) }
                    try {
                        backend.fetchAvailable(normalized)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("ExtensionRemoteDS", "JS index fetch failed: ${e.message}")
                        null
                    }
                }
                val jsExtensions = jsFetch?.extensions.orEmpty()

                // Only report failure when every repository failed and the JavaScript backend
                // did not serve anything either — a repo that responds with a legitimately empty
                // list still counts as a success, and partial results are far more useful to the
                // user than an empty error state.
                //
                // The condition asks whether an index was *served*, not whether it produced
                // extensions. Those differ for a JavaScript-only repository whose index is valid
                // but empty: its APK endpoints legitimately 404, the JS list is legitimately
                // empty, and reading emptiness as failure would show "all repositories failed"
                // to a user whose setup is working exactly as intended.
                if (successCount == 0 && failures.isNotEmpty() && jsFetch?.servedAnyIndex != true) {
                    val (firstUrl, firstError) = failures.first()
                    val exception = ExtensionFetchException(
                        "All ${failures.size} extension repositories failed " +
                            "(first: $firstUrl — ${firstError.message})",
                        firstError
                    )
                    // Preserve the other repos' errors for debugging.
                    failures.drop(1).forEach { (_, error) -> exception.addSuppressed(error) }
                    // And the JavaScript side's, which is the one that matters for a
                    // JavaScript-only repository: without it the user is shown a 404 on an APK
                    // index they never had, while the real fault — a malformed js/index.json —
                    // goes unmentioned.
                    jsFetch?.firstFailure?.let { exception.addSuppressed(it) }
                    return@withContext Result.failure(exception)
                }

                // Deduplicate by package name preferring highest versionCode.
                //
                // Both backends share this one namespace, and that is a requirement rather than
                // an oversight: the DAO keys on pkgName (`getExtensionByPkgName`,
                // `deleteByPkgName`, `updateStatus`), so two rows sharing one would make an
                // uninstall delete both. A JavaScript source id colliding with an Android
                // package name takes a deliberately package-shaped id, and one row surviving is
                // far better than a pair the uninstall path cannot tell apart.
                val merged = (extensions + jsExtensions)
                    .groupBy { it.pkgName }
                    .values
                    .map { candidates ->
                        candidates.maxByOrNull { it.versionCode } ?: candidates.first()
                    }

                Result.success(merged)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Fetch extensions from a single repository.
     * Tries index.min.json first (common format for Keiyoushi/Komikku/Suwayomi),
     * then falls back to index.json if that fails.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchFromRepository(baseUrl: String): List<Extension> {
        val trimmedBaseUrl = baseUrl.trimEnd('/')

        // Try index.min.json first (more common in third-party repos)
        try {
            return fetchMinifiedIndex(trimmedBaseUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Fall back to standard index.json
            try {
                return fetchStandardIndex(trimmedBaseUrl)
            } catch (e2: Exception) {
                // If both fail, attach the first error for debugging
                e2.addSuppressed(e)
                throw e2
            }
        }
    }

    /**
     * Fetch extensions from index.min.json (Keiyoushi/Komikku/Suwayomi format).
     *
     * H-1: Replaced `error()` (throws [IllegalStateException]) with
     * [ExtensionFetchException], a domain-specific exception that is caught by the
     * [Result] wrapper in [fetchAvailableExtensions] and surfaced to the UI layer
     * instead of crashing the app.
     */
    private suspend fun fetchMinifiedIndex(baseUrl: String): List<Extension> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + REPO_INDEX_MIN_PATH)
                .build()

            val responseBody = httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw ExtensionFetchException("HTTP ${response.code} fetching $baseUrl$REPO_INDEX_MIN_PATH")
                }
                response.body?.string()
                    ?: throw ExtensionFetchException("Empty response body from $baseUrl$REPO_INDEX_MIN_PATH")
            }

            val minifiedExtensions = json.decodeFromString<List<MinifiedExtensionDto>>(responseBody)
            minifiedExtensions.map { it.toDomain(baseUrl) }
        }
    }

    /**
     * Fetch extensions from index.json (standard format).
     *
     * H-1: Same fix as [fetchMinifiedIndex] — domain exception instead of `error()`.
     */
    private suspend fun fetchStandardIndex(baseUrl: String): List<Extension> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + REPO_INDEX_PATH)
                .build()

            val responseBody = httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw ExtensionFetchException("HTTP ${response.code} fetching $baseUrl$REPO_INDEX_PATH")
                }
                response.body?.string()
                    ?: throw ExtensionFetchException("Empty response body from $baseUrl$REPO_INDEX_PATH")
            }

            val repoResponse = json.decodeFromString(ExtensionRepoResponse.serializer(), responseBody)
            repoResponse.extensions.map { it.toDomain(baseUrl) }
        }
    }

    override suspend fun downloadApk(apkUrl: String, destination: File): Result<File> {
        return withContext(Dispatchers.IO) {
            // Try the standard /apk/ URL first, falling back to /apks/ only on a 404 (wrong
            // folder name). Network errors or other HTTP failures abort immediately, since
            // retrying the same host with a different path would just double the wait. The loop
            // condition (not break/continue) drives termination.
            val candidates = apkUrlCandidates(apkUrl)
            var lastError: Exception = ExtensionFetchException("No APK URL to download from $apkUrl")
            var downloaded: File? = null
            var abort = false
            var index = 0
            while (downloaded == null && !abort && index < candidates.size) {
                val url = candidates[index]
                index++
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.android.package-archive")
                        .build()

                    httpClient.newCall(request).await().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body
                                ?: throw ExtensionFetchException("Empty APK response body from $url")
                            body.byteStream().use { input ->
                                destination.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            downloaded = destination
                        } else {
                            // H-1: Domain exception instead of error() so the outer Result catches it.
                            lastError = ExtensionFetchException("HTTP ${response.code} downloading APK from $url")
                            // Only a 404 (wrong folder name) is worth trying the alternate path.
                            abort = response.code != 404
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    abort = true
                }
            }
            downloaded?.let { Result.success(it) } ?: Result.failure(lastError)
        }
    }

}

/**
 * Thrown when fetching extension metadata or downloading an APK fails due to an
 * HTTP error or an empty/malformed response body.
 *
 * This is a domain-specific exception that is caught by the [Result] wrappers in
 * [ExtensionRemoteDataSourceImpl] and surfaced to callers as a [Result.failure],
 * preventing unhandled [IllegalStateException] crashes (audit finding H-1).
 */
class ExtensionFetchException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Convert [ExtensionDto] to the [Extension] domain model. */
private fun ExtensionDto.toDomain(baseUrl: String): Extension {
    // Prefer a non-blank apk_url, fall back to the minified-style apk filename. Either may
    // be relative, so both go through resolveApkUrl which prepends the repo base + /apk/
    // as needed. Blank strings are treated as absent so an empty apk_url can't shadow a
    // valid apk filename.
    val resolvedApkUrl = (apkUrl?.takeIf { it.isNotBlank() } ?: apk?.takeIf { it.isNotBlank() })
        ?.let { resolveApkUrl(baseUrl, it) }
    return Extension(
        id = id,
        pkgName = pkgName,
        name = name,
        versionCode = versionCode,
        versionName = versionName,
        sources = sources.map { it.toDomain() },
        status = InstallStatus.AVAILABLE,
        apkPath = null,
        apkUrl = resolvedApkUrl,
        iconUrl = iconUrl,
        lang = lang,
        isNsfw = isNsfw,
        installDate = null,
        signatureHash = signature,
        isEnabled = true
    )
}

private fun ExtensionSourceDto.toDomain(): ExtensionSource {
    return ExtensionSource(
        id = id,
        name = name,
        lang = lang,
        baseUrl = baseUrl,
        supportsSearch = supportsSearch,
        supportsLatest = supportsLatest,
    )
}

/** Convert [MinifiedExtensionDto] to the [Extension] domain model. */
private fun MinifiedExtensionDto.toDomain(baseUrl: String): Extension {
    return Extension(
        id = pkg.hashCode().toLong(), // Generate ID from package name
        pkgName = pkg,
        name = name,
        versionCode = code,
        versionName = version,
        sources = sources.map { it.toDomain() },
        status = InstallStatus.AVAILABLE,
        apkPath = null,
        apkUrl = resolveApkUrl(baseUrl, apk),
        iconUrl = resolveIconUrl(baseUrl, icon, pkg),
        lang = lang,
        isNsfw = nsfw == 1,
        installDate = null,
        signatureHash = null, // Signature not provided in minified format
        isEnabled = true,
        hasReadme = hasReadme,
        hasChangelog = hasChangelog,
        // hasCloudflare is a per-source flag in the minified format (0/1 int on each source).
        // We aggregate it to the extension level: true if any source requires Cloudflare bypass.
        hasCloudflare = sources.any { it.hasCloudflare == 1 }
    )
}

private fun MinifiedExtensionSourceDto.toDomain(): ExtensionSource {
    return ExtensionSource(
        id = id.toLongOrNull() ?: id.hashCode().toLong(), // Parse ID or hash if not numeric
        name = name,
        lang = lang,
        baseUrl = baseUrl,
        supportsSearch = true, // Not specified in minified format, default to true
        supportsLatest = true, // Not specified in minified format, default to true
    )
}

/**
 * Resolve APK URL from a relative filename.
 * Keiyoushi/Mihon repos store APKs under {base}/apk/{filename} (singular); some forks use
 * {base}/apks/{filename}. We build the standard /apk/ URL here, and [downloadApk] falls back
 * to the /apks/ variant if the first request 404s.
 */
private fun resolveApkUrl(baseUrl: String, apkPath: String): String {
    return if (apkPath.startsWith("http://") || apkPath.startsWith("https://")) {
        apkPath
    } else {
        val cleanPath = apkPath.trimStart('/')
        // If the path already contains 'apk/' or 'apks/', don't prepend it.
        if (cleanPath.startsWith("apk/") || cleanPath.startsWith("apks/")) {
            "$baseUrl/$cleanPath"
        } else {
            "$baseUrl/apk/$cleanPath"
        }
    }
}

/**
 * Candidate APK URLs to try in order. Repos disagree on the APK folder name — the standard
 * Keiyoushi/Mihon layout is `/apk/`, while some forks use `/apks/`. Trying both makes installs
 * robust regardless of which layout a repo uses.
 */
private fun apkUrlCandidates(apkUrl: String): List<String> = when {
    apkUrl.contains("/apk/") -> listOf(apkUrl, apkUrl.replaceFirst("/apk/", "/apks/"))
    apkUrl.contains("/apks/") -> listOf(apkUrl, apkUrl.replaceFirst("/apks/", "/apk/"))
    else -> listOf(apkUrl)
}

/**
 * Resolve icon URL from relative path or null.
 * When absent from the index (all four standard repos omit it), fall back to the
 * conventional location: {base}/icon/{pkgName}.png — matching Komikku's behaviour.
 *
 * For relative paths (e.g. "icon/pkg.png"), prepends the base URL correctly without
 * duplicating path segments.
 */
private fun resolveIconUrl(baseUrl: String, iconPath: String?, pkgName: String): String {
    return when {
        iconPath == null -> "$baseUrl/icon/$pkgName.png"
        iconPath.startsWith("http://") || iconPath.startsWith("https://") -> iconPath
        else -> "$baseUrl/${iconPath.trimStart('/')}"
    }
}
