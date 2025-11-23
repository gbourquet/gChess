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

import com.gchess.chess.application.usecase.GetGameUseCase
import com.gchess.chess.application.usecase.MakeMoveUseCase
import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.model.Move
import com.gchess.shared.domain.model.Player
import com.gchess.chess.domain.service.ChessRules
import com.gchess.chess.infrastructure.adapter.driver.dto.toDTO
import com.gchess.infrastructure.config.JwtConfig
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.UserId
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
data class MoveRequest(
    val from: String,
    val to: String,
    val promotion: String? = null
)

/**
 * Helper function to convert UserId (from JWT) to Player (from Game).
 * This conversion is done in the infrastructure layer (GameRoutes) to maintain
 * Chess context isolation - Chess use cases never manipulate UserId directly.
 *
 * @param game The game containing the players
 * @param userId The user ID extracted from JWT token
 * @return The Player matching the userId, or null if user is not a participant
 */
private fun findPlayerByUserId(game: Game, userId: UserId): Player? {
    return when {
        game.whitePlayer.userId == userId -> game.whitePlayer
        game.blackPlayer.userId == userId -> game.blackPlayer
        else -> null
    }
}

fun Application.configureGameRoutes() {
    val getGameUseCase by inject<GetGameUseCase>()
    val makeMoveUseCase by inject<MakeMoveUseCase>()
    val chessRules by inject<ChessRules>()

    routing {
        route("/api/games") {
            // Protected routes - require JWT authentication
            authenticate("jwt-auth") {
                // Note: Game creation has been removed - games are now created ONLY via matchmaking
                // See /api/matchmaking/queue endpoint for automatic game creation

                // Make a move (userId extracted from JWT and converted to Player)
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
                        // 1. Extract userId from JWT token
                        val principal = call.principal<JWTPrincipal>()
                            ?: return@post call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to "Invalid or missing token")
                            )

                        val userId = JwtConfig.extractUserId(principal.payload)

                        // 2. Get the game
                        val id = call.parameters["id"] ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing game id"
                        )
                        val gameId = GameId.fromString(id)

                        val game = getGameUseCase.execute(gameId)
                            ?: return@post call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "Game not found")
                            )

                        // 3. CRITICAL: Convert UserId → Player (infrastructure layer responsibility)
                        // This maintains Chess context isolation - use cases never see UserId
                        val player = findPlayerByUserId(game, userId)
                            ?: return@post call.respond(
                                HttpStatusCode.Forbidden,
                                mapOf("error" to "You are not a participant in this game")
                            )

                        // 4. Parse move request
                        val moveRequest = call.receive<MoveRequest>()
                        val move = Move.fromAlgebraic("${moveRequest.from}${moveRequest.to}")

                        // 5. Execute move with Player (not UserId) - Chess use case stays isolated
                        val result = makeMoveUseCase.execute(gameId, player, move)
                        result.fold(
                            onSuccess = { updatedGame -> call.respond(updatedGame.toDTO(chessRules)) },
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
