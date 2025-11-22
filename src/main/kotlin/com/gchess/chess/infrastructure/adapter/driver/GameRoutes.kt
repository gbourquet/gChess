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
package com.gchess.chess.infrastructure.adapter.driver

import com.gchess.chess.application.usecase.CreateGameUseCase
import com.gchess.chess.application.usecase.GetGameUseCase
import com.gchess.chess.application.usecase.MakeMoveUseCase
import com.gchess.chess.domain.model.Move
import com.gchess.chess.domain.service.ChessRules
import com.gchess.chess.infrastructure.adapter.driver.dto.toDTO
import com.gchess.infrastructure.config.JwtConfig
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId
import io.bkbn.kompendium.core.metadata.GetInfo
import io.bkbn.kompendium.core.metadata.PostInfo
import io.bkbn.kompendium.core.plugin.NotarizedRoute
import io.bkbn.kompendium.oas.payload.Parameter
import io.bkbn.kompendium.json.schema.definition.TypeDefinition
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class CreateGameRequest(
    val whitePlayerId: String,
    val blackPlayerId: String,
    val initialPosition: String? = null // Optional FEN string for custom starting positions
)

@Serializable
data class MoveRequest(
    val from: String,
    val to: String,
    val promotion: String? = null
    // Phase 5: playerId now comes from JWT token, not request body
)

fun Application.configureGameRoutes(enableOpenApiDocs: Boolean = true) {
    val createGameUseCase by inject<CreateGameUseCase>()
    val getGameUseCase by inject<GetGameUseCase>()
    val makeMoveUseCase by inject<MakeMoveUseCase>()
    val chessRules by inject<ChessRules>()

    routing {
        route("/api/games") {
            // Protected routes - require JWT authentication
            authenticate("jwt-auth") {
                // Create a new game
                route("") {
                    install(NotarizedRoute()) {
                        tags = setOf("Games")
                        post = PostInfo.builder {
                            summary("Create a new chess game")
                            description("Create a new game between two players. Both players must exist in the system.")
                            security = mapOf("JWT" to listOf())
                            request {
                                requestType<CreateGameRequest>()
                                description("Player IDs for white and black")
                            }
                            response {
                                responseCode(HttpStatusCode.Created)
                                responseType<com.gchess.chess.infrastructure.adapter.driver.dto.GameDTO>()
                                description("Game successfully created")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.BadRequest)
                                responseType<Map<String, String>>()
                                description("Invalid player IDs or players don't exist")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.Unauthorized)
                                responseType<Map<String, String>>()
                                description("Missing or invalid JWT token")
                            }
                        }
                    }

                    post {
                        val request = call.receive<CreateGameRequest>()

                        val whitePlayerId = try {
                            PlayerId.fromString(request.whitePlayerId)
                        } catch (e: Exception) {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Invalid white player ID format")
                            )
                        }

                        val blackPlayerId = try {
                            PlayerId.fromString(request.blackPlayerId)
                        } catch (e: Exception) {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Invalid black player ID format")
                            )
                        }

                        val result = createGameUseCase.execute(whitePlayerId, blackPlayerId, request.initialPosition)
                        result.fold(
                            onSuccess = { game ->
                                call.respond(HttpStatusCode.Created, game.toDTO(chessRules))
                            },
                            onFailure = { error ->
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to (error.message ?: "Failed to create game"))
                                )
                            }
                        )
                    }
                }

                // Make a move (playerId extracted from JWT)
                route("/{id}/moves") {
                    install(NotarizedRoute()) {
                        tags = setOf("Games")
                        post = PostInfo.builder {
                            summary("Make a chess move")
                            description("Make a move in a game. The player ID is extracted from the JWT token and validated.")
                            security = mapOf("JWT" to listOf())
                            parameters = listOf(
                                Parameter(
                                    name = "id",
                                    `in` = Parameter.Location.path,
                                    schema = TypeDefinition.STRING,
                                    description = "Game ID (ULID format)"
                                )
                            )
                            request {
                                requestType<MoveRequest>()
                                description("Move to execute (algebraic notation)")
                            }
                            response {
                                responseCode(HttpStatusCode.OK)
                                responseType<com.gchess.chess.infrastructure.adapter.driver.dto.GameDTO>()
                                description("Move successfully executed")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.BadRequest)
                                responseType<Map<String, String>>()
                                description("Invalid move, not player's turn, or game already finished")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.Unauthorized)
                                responseType<Map<String, String>>()
                                description("Missing or invalid JWT token")
                            }
                        }
                    }

                    post {
                        // Extract player ID from JWT token
                        val principal = call.principal<JWTPrincipal>()
                            ?: return@post call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to "Invalid or missing token")
                            )

                        val playerId = JwtConfig.extractPlayerId(principal.payload)

                        val id = call.parameters["id"] ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing game id"
                        )
                        val gameId = GameId.fromString(id)
                        val moveRequest = call.receive<MoveRequest>()

                        val move = Move.fromAlgebraic("${moveRequest.from}${moveRequest.to}")

                        val result = makeMoveUseCase.execute(gameId, playerId, move)
                        result.fold(
                            onSuccess = { game -> call.respond(game.toDTO(chessRules)) },
                            onFailure = { error ->
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to (error.message ?: "Invalid move"))
                                )
                            }
                        )
                    }
                }
            }

            // Public route - no authentication required
            route("/{id}") {
                install(NotarizedRoute()) {
                    tags = setOf("Games")
                    get = GetInfo.builder {
                        summary("Get game by ID")
                        description("Retrieve the current state of a chess game by its ID. No authentication required.")
                        parameters = listOf(
                            Parameter(
                                name = "id",
                                `in` = Parameter.Location.path,
                                schema = TypeDefinition.STRING,
                                description = "Game ID (ULID format)"
                            )
                        )
                        response {
                            responseCode(HttpStatusCode.OK)
                            responseType<com.gchess.chess.infrastructure.adapter.driver.dto.GameDTO>()
                            description("Game found")
                        }
                        canRespond {
                            responseCode(HttpStatusCode.BadRequest)
                            responseType<String>()
                            description("Invalid game ID format")
                        }
                        canRespond {
                            responseCode(HttpStatusCode.NotFound)
                            responseType<String>()
                            description("Game not found")
                        }
                    }
                }

                get {
                    val id = call.parameters["id"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        "Missing game id"
                    )
                    val gameId = GameId.fromString(id)
                    val game = getGameUseCase.execute(gameId)
                    if (game == null) {
                        call.respond(HttpStatusCode.NotFound, "Game not found")
                    } else {
                        call.respond(game.toDTO(chessRules))
                    }
                }
            }
        }
    }
}
