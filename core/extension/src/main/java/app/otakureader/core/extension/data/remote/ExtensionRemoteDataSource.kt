package app.otakureader.core.extension.data.remote

import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import app.otakureader.core.extension.domain.repository.ExtensionRepoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    val apkUrl: String,

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
) : ExtensionRemoteDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val REPO_INDEX_PATH = "/index.json"
        private const val REPO_INDEX_MIN_PATH = "/index.min.json"

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

                repositories.forEach { baseUrl ->
                    val repoExtensions = fetchFromRepository(baseUrl)
                    extensions.addAll(repoExtensions)
                }

                // Deduplicate by package name preferring highest versionCode
                val merged = extensions
                    .groupBy { it.pkgName }
                    .values
                    .map { candidates ->
                        candidates.maxByOrNull { it.versionCode } ?: candidates.first()
                    }

                Result.success(merged)
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
    private suspend fun fetchFromRepository(baseUrl: String): List<Extension> {
        val trimmedBaseUrl = baseUrl.trimEnd('/')

        // Try index.min.json first (more common in third-party repos)
        try {
            return fetchMinifiedIndex(trimmedBaseUrl)
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
     */
    private suspend fun fetchMinifiedIndex(baseUrl: String): List<Extension> {
        val request = Request.Builder()
            .url(baseUrl + REPO_INDEX_MIN_PATH)
            .build()

        val responseBody = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("Empty body")
        }

        val minifiedExtensions = json.decodeFromString<List<MinifiedExtensionDto>>(responseBody)
        return minifiedExtensions.map { it.toDomain(baseUrl) }
    }

    /**
     * Fetch extensions from index.json (standard format).
     */
    private suspend fun fetchStandardIndex(baseUrl: String): List<Extension> {
        val request = Request.Builder()
            .url(baseUrl + REPO_INDEX_PATH)
            .build()

        val responseBody = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("Empty body")
        }

        val repoResponse = json.decodeFromString(ExtensionRepoResponse.serializer(), responseBody)
        return repoResponse.extensions.map { it.toDomain() }
    }

    override suspend fun downloadApk(apkUrl: String, destination: File): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(apkUrl)
                    .header("Accept", "application/vnd.android.package-archive")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Empty body")
                    body.byteStream().use { input ->
                        destination.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                Result.success(destination)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

}

/** Convert [ExtensionDto] to the [Extension] domain model. */
private fun ExtensionDto.toDomain(): Extension {
    return Extension(
        id = id,
        pkgName = pkgName,
        name = name,
        versionCode = versionCode,
        versionName = versionName,
        sources = sources.map { it.toDomain() },
        status = InstallStatus.AVAILABLE,
        apkPath = null,
        apkUrl = apkUrl,
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
        iconUrl = icon?.let { resolveIconUrl(baseUrl, it) },
        lang = lang,
        isNsfw = nsfw == 1,
        installDate = null,
        signatureHash = null, // Signature not provided in minified format
        isEnabled = true
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
 * Resolve APK URL from relative or absolute path.
 * If the APK path is relative, prepend the repository base URL.
 */
private fun resolveApkUrl(baseUrl: String, apkPath: String): String {
    return if (apkPath.startsWith("http://") || apkPath.startsWith("https://")) {
        apkPath
    } else {
        "$baseUrl/${apkPath.trimStart('/')}"
    }
}

/**
 * Resolve icon URL from relative or absolute path.
 * If the icon path is relative, prepend the repository base URL.
 */
private fun resolveIconUrl(baseUrl: String, iconPath: String): String {
    return if (iconPath.startsWith("http://") || iconPath.startsWith("https://")) {
        iconPath
    } else {
        "$baseUrl/${iconPath.trimStart('/')}"
    }
}
