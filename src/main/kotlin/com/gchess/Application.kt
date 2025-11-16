package com.gchess

import com.gchess.infrastructure.adapter.input.configureGameRoutes
import com.gchess.infrastructure.config.appModule
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
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

    // Configure routes
    configureGameRoutes()

    routing {
        get("/") {
            call.respondText("gChess API is running!")
        }
    }
}
