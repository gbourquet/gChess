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

import com.gchess.chess.application.usecase.GetGameMovesResult
import com.gchess.chess.application.usecase.GetGameMovesUseCase
import com.gchess.chess.application.usecase.GetUserGamesUseCase
import com.gchess.chess.infrastructure.adapter.driver.dto.GameSummaryDTO
import com.gchess.chess.infrastructure.adapter.driver.dto.MoveSummaryDTO
import com.gchess.chess.infrastructure.adapter.driver.dto.toSummaryDTO
import com.gchess.infrastructure.config.JwtConfig
import com.gchess.shared.domain.model.GameId
import io.bkbn.kompendium.core.metadata.GetInfo
import io.bkbn.kompendium.core.plugin.NotarizedRoute
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Routes REST pour l'historique des parties.
 *
 * GET /api/history/games                    — liste des parties du joueur authentifié (JWT requis)
 * GET /api/history/games/{gameId}/moves     — coups d'une partie (JWT requis, participants uniquement)
 */
fun Application.configureGameHistoryRoutes() {
    val getUserGamesUseCase by inject<GetUserGamesUseCase>()
    val getGameMovesUseCase by inject<GetGameMovesUseCase>()

    routing {
        authenticate("jwt-auth") {
            route("/api/history") {

                route("/games") {
                    install(NotarizedRoute()) {
                        tags = setOf("History")
                        get = GetInfo.builder {
                            summary("List player's games")
                            description("Returns all games the authenticated player has participated in")
                            response {
                                responseCode(HttpStatusCode.OK)
                                responseType<List<GameSummaryDTO>>()
                                description("List of game summaries")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.Unauthorized)
                                responseType<Map<String, String>>()
                                description("Missing or invalid JWT token")
                            }
                            security(mapOf("bearerAuth" to emptyList()))
                        }
                    }

                    get {
                        val principal = call.principal<JWTPrincipal>()!!
                        val userId = JwtConfig.extractUserId(principal.payload)

                        val games = getUserGamesUseCase.execute(userId)
                        call.respond(HttpStatusCode.OK, games.map { it.toSummaryDTO() })
                    }
                }

                route("/games/{gameId}/moves") {
                    install(NotarizedRoute()) {
                        tags = setOf("History")
                        get = GetInfo.builder {
                            summary("List moves of a game")
                            description("Returns all moves of a given game. Only the two participants can access this endpoint.")
                            response {
                                responseCode(HttpStatusCode.OK)
                                responseType<List<MoveSummaryDTO>>()
                                description("Ordered list of moves")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.Unauthorized)
                                responseType<Map<String, String>>()
                                description("Missing or invalid JWT token")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.Forbidden)
                                responseType<Map<String, String>>()
                                description("Authenticated user is not a participant in this game")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.NotFound)
                                responseType<Map<String, String>>()
                                description("Game not found")
                            }
                            security(mapOf("bearerAuth" to emptyList()))
                        }
                    }

                    get {
                        val principal = call.principal<JWTPrincipal>()!!
                        val userId = JwtConfig.extractUserId(principal.payload)
                        val gameId = GameId(call.parameters["gameId"]!!)

                        when (val result = getGameMovesUseCase.execute(gameId, userId)) {
                            is GetGameMovesResult.Success -> {
                                val moves = result.moves.mapIndexed { index, move -> move.toSummaryDTO(index) }
                                call.respond(HttpStatusCode.OK, moves)
                            }
                            is GetGameMovesResult.GameNotFound ->
                                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Game not found"))
                            is GetGameMovesResult.Forbidden ->
                                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You are not a participant in this game"))
                        }
                    }
                }
            }
        }
    }
}
