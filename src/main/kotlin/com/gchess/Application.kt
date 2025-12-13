/*
 * Copyright (c) 2024-2025 Guillaume Bourquet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.gchess

import com.gchess.bot.infrastructure.adapter.driver.configureBotRoutes
import com.gchess.chess.infrastructure.adapter.driver.configureGameWebSocketRoutes
import com.gchess.infrastructure.config.EnvironmentConfig
import com.gchess.infrastructure.config.JwtConfig
import com.gchess.infrastructure.config.OpenApiConfig
import com.gchess.infrastructure.config.appModule
import com.gchess.infrastructure.health.configureHealthRoutes
import com.gchess.matchmaking.infrastructure.adapter.driver.configureMatchmakingWebSocketRoutes
import com.gchess.user.infrastructure.adapter.driver.configureAuthRoutes
import io.bkbn.kompendium.core.plugin.NotarizedApplication
import io.bkbn.kompendium.oas.OpenApiSpec
import io.bkbn.kompendium.oas.info.Contact
import io.bkbn.kompendium.oas.info.Info
import io.bkbn.kompendium.oas.server.Server
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.net.URI
import kotlin.time.Duration.Companion.seconds

fun main() {
    val port = EnvironmentConfig.port
    val environment = EnvironmentConfig.environment

    println("🚀 Starting gChess backend on port $port (environment: $environment)")

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // Koin dependency injection
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    // Content negotiation for JSON
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // CORS configuration (environment-aware)
    install(CORS) {
        // Allow origins based on environment configuration
        EnvironmentConfig.corsOrigins.forEach { origin ->
            val uri = URI(origin)
            allowHost(uri.host + (if (uri.port > 0) ":${uri.port}" else ""), schemes = listOf(uri.scheme))
        }

        // Allow common HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Patch)

        // Allow common headers including Authorization for JWT
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)

        // Allow credentials (cookies, authorization headers)
        allowCredentials = true

        // Allow WebSocket upgrade headers
        allowHeader(HttpHeaders.SecWebSocketProtocol)
        allowHeader(HttpHeaders.SecWebSocketVersion)
        allowHeader(HttpHeaders.SecWebSocketKey)
        allowHeader(HttpHeaders.SecWebSocketExtensions)

        // Expose custom headers if needed
        exposeHeader(HttpHeaders.Authorization)
    }

    // WebSocket configuration
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // OpenAPI Documentation
    install(NotarizedApplication()) {
        spec =   { OpenApiSpec(
            info = Info(
                title = OpenApiConfig.TITLE,
                version = OpenApiConfig.VERSION,
                description = OpenApiConfig.DESCRIPTION.trimIndent(),
                contact = Contact(
                    name = OpenApiConfig.Contact.NAME,
                    email = OpenApiConfig.Contact.EMAIL
                )
            ),
            servers = mutableListOf(
                Server(
                    url = URI(OpenApiConfig.Server.LOCAL_URL),
                    description = OpenApiConfig.Server.LOCAL_DESCRIPTION
                )
            )
        ) }
    }

    // JWT Authentication
    install(Authentication) {
        jwt("jwt-auth") {
            realm = JwtConfig.REALM
            verifier(JwtConfig.makeVerifier())
            validate { credential ->
                // Validate that the token has a userId claim
                if (credential.payload.getClaim("userId").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    // Configure routes
    configureHealthRoutes()     // Health check endpoints for monitoring and orchestration
    configureAuthRoutes()       // User context auth routes (register, login) - public
    configureBotRoutes()        // Bot context routes (GET /api/bots) - public, read-only
    configureMatchmakingWebSocketRoutes()  // Matchmaking webSocket routes for real-time communication
    configureGameWebSocketRoutes()  // Game webSocket routes for real-time communication (players and spectators)

    routing {
        get("/") {
            call.respondText("gChess API is running!")
        }

        // OpenAPI spec is available at:
        // - /openapi.json (dynamic, generated by Kompendium)
        // - A static copy is also generated at build time in src/main/resources/openapi/openapi.json
        // To view with Swagger UI, visit https://petstore.swagger.io/ and load http://localhost:8080/openapi.json
    }
}
