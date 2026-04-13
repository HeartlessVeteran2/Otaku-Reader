package app.otakureader.server.routes

import app.otakureader.server.config.AppConfig
import app.otakureader.server.model.ErrorResponse
import app.otakureader.server.model.SnapshotResponse
import app.otakureader.server.model.UploadRequest
import app.otakureader.server.model.UploadResponse
import app.otakureader.server.service.SyncService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Sync API routes.
 */
fun Route.syncRoutes(config: AppConfig) {
    val syncService = SyncService(config)

    route("/sync") {

        /**
         * POST /sync/upload
         * Upload a sync snapshot. Overwrites any existing snapshot.
         */
        post("/upload") {
            try {
                val request = call.receive<UploadRequest>()

                val result = syncService.storeSnapshot(request.data, request.timestamp)
                if (result.isSuccess) {
                    call.respond(
                        HttpStatusCode.OK,
                        UploadResponse(
                            success = true,
                            timestamp = request.timestamp,
                            size = result.getOrDefault(request.data.length)
                        )
                    )
                } else {
                    // H-9: Log the full exception server-side; return a generic message to the
                    // client so that internal implementation details are never exposed.
                    val cause = result.exceptionOrNull()
                // System.err.println("[SyncRoutes] storeSnapshot failed: ${cause?.message}")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to store snapshot. Please try again later.")
                    )
                }
            } catch (e: Exception) {
                // H-9: Log the full exception server-side; return a generic message to the
                // client so that internal implementation details are never exposed.
                // System.err.println("[SyncRoutes] upload request error: ${e.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid request. Please check the request format and try again.")
                )
            }
        }

        /**
         * GET /sync/download
         * Download the latest sync snapshot.
         */
        get("/download") {
            val snapshot = syncService.getSnapshot()

            if (snapshot != null) {
                call.respond(
                    HttpStatusCode.OK,
                    SnapshotResponse(
                        data = snapshot.first,
                        timestamp = snapshot.second,
                        exists = true
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    SnapshotResponse(
                        data = null,
                        timestamp = null,
                        exists = false
                    )
                )
            }
        }

        /**
         * GET /sync/timestamp
         * Get the timestamp of the latest snapshot.
         */
        get("/timestamp") {
            val timestamp = syncService.getTimestamp()

            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "timestamp" to timestamp,
                    "exists" to (timestamp != null)
                )
            )
        }

        /**
         * DELETE /sync
         * Delete the stored snapshot.
         */
        delete {
            val result = syncService.deleteSnapshot()
            if (result.isSuccess) {
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("success" to true)
                )
            } else {
                // H-9: Log the full exception server-side; return a generic message to the
                // client so that internal implementation details are never exposed.
                val cause = result.exceptionOrNull()
                // System.err.println("[SyncRoutes] deleteSnapshot failed: ${cause?.message}")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to delete snapshot. Please try again later.")
                )
            }
        }
    }
}
