package app.otakureader.core.extension.data.remote

import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import kotlinx.coroutines.Dispatchers
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

@Serializable
data class ExtensionRepoResponse(
    @SerialName("extensions")
    val extensions: List<ExtensionDto>,

    @SerialName("last_modified")
    val lastModified: Long,
)

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

    /**
     * Get the base URL for the extension repository.
     */
    fun getRepoBaseUrl(): String
}

class ExtensionRemoteDataSourceImpl(
    private val repoBaseUrl: String,
    private val httpClient: OkHttpClient = createDefaultClient(),
) : ExtensionRemoteDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val REPO_INDEX_PATH = "/index.json"

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
                val request = Request.Builder()
                    .url("$repoBaseUrl$REPO_INDEX_PATH")
                    .build()
                val responseBody = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    response.body?.string() ?: error("Empty body")
                }
                val repoResponse = json.decodeFromString(ExtensionRepoResponse.serializer(), responseBody)
                Result.success(repoResponse.extensions.map { it.toDomain() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
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

    override fun getRepoBaseUrl(): String = repoBaseUrl
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