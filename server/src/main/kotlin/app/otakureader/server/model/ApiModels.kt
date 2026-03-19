package app.otakureader.server.model

import kotlinx.serialization.Serializable

/**
 * Health check response.
 */
@Serializable
data class HealthResponse(
    val status: String,
    val version: String = "1.0.0"
)

/**
 * Request to upload a sync snapshot.
 */
@Serializable
data class UploadRequest(
    val data: String,
    val timestamp: Long
)

/**
 * Response after successful upload.
 */
@Serializable
data class UploadResponse(
    val success: Boolean,
    val timestamp: Long,
    val size: Int
)

/**
 * Response containing the stored snapshot data.
 */
@Serializable
data class SnapshotResponse(
    val data: String?,
    val timestamp: Long?,
    val exists: Boolean
)

/**
 * Generic error response.
 */
@Serializable
data class ErrorResponse(
    val error: String
)
