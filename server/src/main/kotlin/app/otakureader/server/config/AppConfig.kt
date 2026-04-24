package app.otakureader.server.config

import java.io.File
import java.util.UUID

/**
 * Server configuration loaded from environment variables.
 */
data class AppConfig(
    val host: String,
    val port: Int,
    val authToken: String,
    val storagePath: String,
    val allowedOrigins: List<String>
) {
    companion object {
        fun load(): AppConfig {
            val authToken = System.getenv("AUTH_TOKEN")
            val allowedOrigins = System.getenv("ALLOWED_ORIGINS")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            if (authToken.isNullOrBlank()) {
                // Generate a random token and log it once
                val generatedToken = UUID.randomUUID().toString()
                println("⚠️  WARNING: AUTH_TOKEN not set. Generated random token for this session:")
                println("   $generatedToken")
                println("   Set AUTH_TOKEN environment variable for production use.")
                return AppConfig(
                    host = System.getenv("HOST") ?: "0.0.0.0",
                    port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
                    authToken = generatedToken,
                    storagePath = System.getenv("STORAGE_PATH") ?: "/app/data",
                    allowedOrigins = allowedOrigins
                )
            }

            return AppConfig(
                host = System.getenv("HOST") ?: "0.0.0.0",
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
                authToken = authToken,
                storagePath = System.getenv("STORAGE_PATH") ?: "/app/data",
                allowedOrigins = allowedOrigins
            )
        }
    }

    init {
        // Ensure storage directory exists
        File(storagePath).mkdirs()
    }
}
