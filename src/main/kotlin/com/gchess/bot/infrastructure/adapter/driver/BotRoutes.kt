package com.gchess.bot.infrastructure.adapter.driver

import com.gchess.bot.application.usecase.GetBotUseCase
import com.gchess.bot.application.usecase.ListBotsUseCase
import com.gchess.bot.domain.model.BotId
import com.gchess.bot.infrastructure.adapter.driver.dto.BotDTO
import com.gchess.bot.infrastructure.adapter.driver.dto.toDTO
import io.bkbn.kompendium.core.metadata.GetInfo
import io.bkbn.kompendium.core.plugin.NotarizedRoute
import io.bkbn.kompendium.json.schema.definition.TypeDefinition
import io.bkbn.kompendium.oas.payload.Parameter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * HTTP routes for bot operations.
 * Public read-only endpoints for listing and viewing bots.
 */
fun Application.configureBotRoutes() {
    val listBotsUseCase by inject<ListBotsUseCase>()
    val getBotUseCase by inject<GetBotUseCase>()

    routing {
        route("/api/bots") {
            // GET /api/bots - List all available bots
            install(NotarizedRoute()) {
                tags = setOf("Bots")
                get = GetInfo.builder {
                    summary("List all bots")
                    description("Retrieve a list of all available bot opponents with their difficulty levels")
                    response {
                        responseCode(HttpStatusCode.OK)
                        responseType<List<BotDTO>>()
                        description("List of all available bots")
                    }
                    canRespond {
                        responseCode(HttpStatusCode.InternalServerError)
                        responseType<Map<String, String>>()
                        description("Failed to retrieve bots")
                    }
                }
            }

            get {
                val result = listBotsUseCase.execute()
                result.fold(
                    onSuccess = { bots ->
                        call.respond(HttpStatusCode.OK, bots.map { it.toDTO() })
                    },
                    onFailure = { error ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (error.message ?: "Failed to list bots"))
                        )
                    }
                )
            }

            // GET /api/bots/{id} - Get a specific bot
            route("/{id}") {
                install(NotarizedRoute()) {
                    tags = setOf("Bots")
                    parameters = listOf(
                        Parameter(
                            name = "id",
                            `in` = Parameter.Location.path,
                            schema = TypeDefinition.STRING,
                            description = "Bot ID (ULID format)"
                        )
                    )
                    get = GetInfo.builder {
                        summary("Get bot by ID")
                        description("Retrieve detailed information about a specific bot opponent")
                        response {
                            responseCode(HttpStatusCode.OK)
                            responseType<BotDTO>()
                            description("Bot details")
                        }
                        canRespond {
                            responseCode(HttpStatusCode.BadRequest)
                            responseType<Map<String, String>>()
                            description("Invalid bot ID format")
                        }
                        canRespond {
                            responseCode(HttpStatusCode.NotFound)
                            responseType<Map<String, String>>()
                            description("Bot not found")
                        }
                    }
                }

                get {
                    val botIdParam = call.parameters["id"]
                    if (botIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing bot ID"))
                        return@get
                    }

                    val botId = try {
                        BotId.fromString(botIdParam)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid bot ID"))
                        return@get
                    }

                    val result = getBotUseCase.execute(botId)
                    result.fold(
                        onSuccess = { bot ->
                            call.respond(HttpStatusCode.OK, bot.toDTO())
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to (error.message ?: "Bot not found"))
                            )
                        }
                    )
                }
            }
        }
    }
}
