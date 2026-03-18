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

                syncService.storeSnapshot(request.data, request.timestamp).fold(
                    onSuccess = { size ->
                        call.respond(
                            HttpStatusCode.OK,
                            UploadResponse(
                                success = true,
                                timestamp = request.timestamp,
                                size = size
                            )
                        )
                    },
                    onFailure = { error ->
                        // Log detailed error server-side
                        call.application.environment.log.error("Failed to store snapshot", error)
                        // Return generic error to client
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to store snapshot")
                        )
                    }
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid request: ${e.message}")
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
            syncService.deleteSnapshot().fold(
                onSuccess = {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("success" to true)
                    )
                },
                onFailure = { error ->
                    // Log detailed error server-side
                    call.application.environment.log.error("Failed to delete snapshot", error)
                    // Return generic error to client
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to delete snapshot")
                    )
                }
            )
        }
    }
}
