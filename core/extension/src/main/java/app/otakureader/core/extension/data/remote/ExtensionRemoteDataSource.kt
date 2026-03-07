package app.otakureader.core.extension.data.remote

import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * DTOs for extension repository API responses.
 * This represents the JSON structure returned by the extension repo server.
 */

@Serializable
data class ExtensionRepoResponse(
    @SerialName("extensions")
    val extensions: List<ExtensionDto>,
    
    @SerialName("last_modified")
    val lastModified: Long
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
    val iconUrl: String?,
    
    @SerialName("lang")
    val lang: String,
    
    @SerialName("is_nsfw")
    val isNsfw: Boolean = false,
    
    @SerialName("signature")
    val signature: String?
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
    val supportsLatest: Boolean = true
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
    private val httpClient: HttpClient = createDefaultClient()
) : ExtensionRemoteDataSource {
    
    companion object {
        private const val REPO_INDEX_PATH = "/index.json"
        private const val TIMEOUT_MS = 30000L
        
        fun createDefaultClient(): HttpClient {
            return HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                    })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = TIMEOUT_MS
                    connectTimeoutMillis = 10000
                    socketTimeoutMillis = TIMEOUT_MS
                }
            }
        }
    }
    
    override suspend fun fetchAvailableExtensions(): Result<List<Extension>> {
        return try {
            val response: ExtensionRepoResponse = httpClient.get("$repoBaseUrl$REPO_INDEX_PATH").body()
            val extensions = response.extensions.map { it.toDomain() }
            Result.success(extensions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun downloadApk(apkUrl: String, destination: File): Result<File> {
        return try {
            val response = httpClient.get(apkUrl) {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.android.package-archive")
                }
            }
            
            val bytes: ByteArray = response.body()
            destination.writeBytes(bytes)
            
            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getRepoBaseUrl(): String = repoBaseUrl
}

/**
 * Extension function to convert DTO to domain model.
 */
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
        iconUrl = iconUrl,
        lang = lang,
        isNsfw = isNsfw,
        installDate = null,
        signatureHash = signature
    )
}

private fun ExtensionSourceDto.toDomain(): ExtensionSource {
    return ExtensionSource(
        id = id,
        name = name,
        lang = lang,
        baseUrl = baseUrl,
        supportsSearch = supportsSearch,
        supportsLatest = supportsLatest
    )
}
