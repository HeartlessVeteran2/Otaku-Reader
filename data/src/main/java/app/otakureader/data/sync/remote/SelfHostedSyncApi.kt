package app.otakureader.data.sync.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit API interface for the self-hosted sync server.
 */
interface SelfHostedSyncApi {

    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("sync/upload")
    suspend fun uploadSnapshot(
        @Header("Authorization") authHeader: String,
        @Body request: UploadRequest
    ): Response<UploadResponse>

    @GET("sync/download")
    suspend fun downloadSnapshot(
        @Header("Authorization") authHeader: String
    ): Response<SnapshotResponse>

    @GET("sync/timestamp")
    suspend fun getTimestamp(
        @Header("Authorization") authHeader: String
    ): Response<TimestampResponse>

    @DELETE("sync")
    suspend fun deleteSnapshot(
        @Header("Authorization") authHeader: String
    ): Response<DeleteResponse>
}

@Serializable
data class HealthResponse(
    val status: String,
    val version: String? = null
)

@Serializable
data class UploadRequest(
    val data: String,
    val timestamp: Long
)

@Serializable
data class UploadResponse(
    val success: Boolean,
    val timestamp: Long,
    val size: Int
)

@Serializable
data class SnapshotResponse(
    val data: String?,
    val timestamp: Long?,
    val exists: Boolean
)

@Serializable
data class TimestampResponse(
    val timestamp: Long?,
    val exists: Boolean
)

@Serializable
data class DeleteResponse(
    val success: Boolean
)
