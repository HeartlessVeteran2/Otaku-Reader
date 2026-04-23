package app.otakureader.server

import app.otakureader.server.config.AppConfig
import app.otakureader.server.model.HealthResponse
import app.otakureader.server.routes.syncRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = AppConfig.load()

    embeddedServer(Netty, port = config.port, host = config.host) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig) {
    install(CallLogging)

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }

    install(RateLimit) {
        global {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
        }
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        })
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Log detailed error server-side
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Internal server error")
            )
        }
    }

    install(Authentication) {
        bearer("auth-bearer") {
            realm = "Otaku Reader Sync Server"
            authenticate { tokenCredential ->
                if (tokenCredential.token == config.authToken) {
                    UserPrincipal("user")
                } else {
                    null
                }
            }
        }
    }

    routing {
        // Health check (no auth required)
        get("/health") {
            call.respond(HealthResponse(status = "OK"))
        }

        // Protected sync routes
        authenticate("auth-bearer") {
            syncRoutes(config)
        }
    }
}

data class UserPrincipal(val username: String) : io.ktor.server.auth.Principal
