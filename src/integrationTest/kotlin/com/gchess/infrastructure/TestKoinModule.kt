package com.gchess.infrastructure

import com.gchess.chess.infrastructure.adapter.driver.configureGameRoutes
import com.gchess.infrastructure.config.JwtConfig
import com.gchess.infrastructure.config.OpenApiConfig
import com.gchess.infrastructure.config.appModule
import com.gchess.matchmaking.infrastructure.adapter.driver.configureMatchmakingRoutes
import com.gchess.user.infrastructure.adapter.driver.configureAuthRoutes
import com.gchess.user.infrastructure.adapter.driver.configureUserRoutes
import io.bkbn.kompendium.core.plugin.NotarizedApplication
import io.bkbn.kompendium.oas.OpenApiSpec
import io.bkbn.kompendium.oas.info.Contact
import io.bkbn.kompendium.oas.info.Info
import io.bkbn.kompendium.oas.server.Server
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.net.URI
import javax.sql.DataSource

/**
 * Test Koin module for integration tests.
 *
 * This module overrides database dependencies to use Testcontainers PostgreSQL:
 * - DataSource: Testcontainers PostgreSQL
 * - DSLContext: Connected to test database with migrations applied
 *
 * All other dependencies are unchanged (use cases, repositories, etc.)
 */
val testDatabaseModule = module {
    // Override DataSource with test version
    single<DataSource>(createdAtStart = true) {
        TestDatabaseConfig.createTestDataSource()
    }

    // Override DSLContext with test version (includes migrations)
    single<DSLContext>(createdAtStart = true) {
        TestDatabaseConfig.createTestDslContext(get())
    }
}

/**
 * Application module for integration tests.
 *
 * Configures the Ktor application with:
 * - Test database (Testcontainers PostgreSQL)
 * - All regular plugins (JSON, OpenAPI, JWT)
 * - All routes
 *
 * Usage in tests:
 * ```
 * testApplication {
 *     application {
 *         testModule()
 *     }
 * }
 * ```
 */
fun Application.testModule() {
    // Koin dependency injection with test database
    install(Koin) {
        slf4jLogger()
        modules(appModule, testDatabaseModule)
    }

    // Content negotiation for JSON
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // OpenAPI Documentation
    install(NotarizedApplication()) {
        spec = OpenApiSpec(
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
        )
    }

    // JWT Authentication
    install(Authentication) {
        jwt("jwt-auth") {
            realm = JwtConfig.REALM
            verifier(JwtConfig.makeVerifier())
            validate { credential ->
                // Validate that the token has a playerId claim
                if (credential.payload.getClaim("playerId").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    // Configure routes
    configureGameRoutes()
    configureAuthRoutes()
    configureUserRoutes()
    configureMatchmakingRoutes()

    routing {
        get("/") {
            call.respondText("gChess API is running (TEST MODE with Testcontainers)!")
        }
    }
}
