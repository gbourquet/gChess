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
package com.gchess.matchmaking.infrastructure.adapter.driver

import com.gchess.infrastructure.config.JwtConfig
import com.gchess.matchmaking.application.usecase.GetMatchStatusUseCase
import com.gchess.matchmaking.application.usecase.JoinMatchmakingUseCase
import com.gchess.matchmaking.application.usecase.LeaveMatchmakingUseCase
import com.gchess.matchmaking.infrastructure.adapter.driver.dto.MatchmakingStatusDTO
import com.gchess.matchmaking.infrastructure.adapter.driver.dto.toDTO
import io.bkbn.kompendium.core.metadata.DeleteInfo
import io.bkbn.kompendium.core.metadata.GetInfo
import io.bkbn.kompendium.core.metadata.PostInfo
import io.bkbn.kompendium.core.plugin.NotarizedRoute
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Configures matchmaking routes for the application.
 *
 * These routes handle:
 * - POST /api/matchmaking/queue - Join matchmaking queue
 * - DELETE /api/matchmaking/queue - Leave matchmaking queue
 * - GET /api/matchmaking/status - Get current matchmaking status
 *
 * All routes require JWT authentication.
 */
fun Application.configureMatchmakingRoutes() {
    val joinMatchmakingUseCase by inject<JoinMatchmakingUseCase>()
    val leaveMatchmakingUseCase by inject<LeaveMatchmakingUseCase>()
    val getMatchStatusUseCase by inject<GetMatchStatusUseCase>()

    routing {
        route("/api/matchmaking") {
            // All matchmaking routes require authentication
            authenticate("jwt-auth") {
                // /api/matchmaking/queue - Join and leave matchmaking
                route("/queue") {
                    install(NotarizedRoute()) {
                        tags = setOf("Matchmaking")

                        // POST /api/matchmaking/queue - Join matchmaking
                        post = PostInfo.builder {
                            summary("Join matchmaking queue")
                            description("""
                                Add the authenticated player to the matchmaking queue.

                                If another player is already waiting, a match is created automatically:
                                - Colors are assigned randomly (50/50)
                                - A chess game is created
                                - Both players receive MATCHED status

                                If no other player is waiting:
                                - Player is added to queue with WAITING status
                                - Poll GET /api/matchmaking/status to check for match
                            """.trimIndent())
                            security = mapOf("JWT" to listOf())
                            response {
                                responseCode(HttpStatusCode.OK)
                                responseType<MatchmakingStatusDTO>()
                                description("Matchmaking status (WAITING or MATCHED)")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.Conflict)
                                responseType<Map<String, String>>()
                                description("Player already in queue or already matched")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.BadRequest)
                                responseType<Map<String, String>>()
                                description("Player does not exist")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.Unauthorized)
                                responseType<Map<String, String>>()
                                description("Missing or invalid JWT token")
                            }
                        }

                        // DELETE /api/matchmaking/queue - Leave matchmaking
                        delete = DeleteInfo.builder {
                            summary("Leave matchmaking queue")
                            description("""
                                Remove the authenticated player from the matchmaking queue.

                                If the player is not in queue, this operation succeeds silently.

                                Note: Once matched, players cannot leave via this endpoint.
                                The match has already been created and they should join the game.
                            """.trimIndent())
                            security = mapOf("JWT" to listOf())
                            response {
                                responseCode(HttpStatusCode.OK)
                                responseType<Map<String, Boolean>>()
                                description("Success (removed: true if was in queue, false otherwise)")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.Unauthorized)
                                responseType<Map<String, String>>()
                                description("Missing or invalid JWT token")
                            }
                        }
                    }

                    post {
                        // Extract user ID from JWT token
                        val principal = call.principal<JWTPrincipal>()
                            ?: return@post call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to "Missing authentication")
                            )

                        val userId = JwtConfig.extractUserId(principal.payload)

                        // Execute join matchmaking use case
                        val result = joinMatchmakingUseCase.execute(userId)

                        result.fold(
                            onSuccess = { matchmakingResult ->
                                call.respond(HttpStatusCode.OK, matchmakingResult.toDTO())
                            },
                            onFailure = { exception ->
                                // Determine appropriate HTTP status based on error
                                val statusCode = when {
                                    exception.message?.contains("already in") == true -> HttpStatusCode.Conflict
                                    exception.message?.contains("already has") == true -> HttpStatusCode.Conflict
                                    exception.message?.contains("does not exist") == true -> HttpStatusCode.BadRequest
                                    else -> HttpStatusCode.InternalServerError
                                }
                                call.respond(statusCode, mapOf("error" to (exception.message ?: "Unknown error")))
                            }
                        )
                    }

                    delete {
                        // Extract user ID from JWT token
                        val principal = call.principal<JWTPrincipal>()
                            ?: return@delete call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to "Missing authentication")
                            )

                        val userId = JwtConfig.extractUserId(principal.payload)

                        // Execute leave matchmaking use case
                        val result = leaveMatchmakingUseCase.execute(userId)

                        result.fold(
                            onSuccess = { removed ->
                                call.respond(HttpStatusCode.OK, mapOf("removed" to removed))
                            },
                            onFailure = { exception ->
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to (exception.message ?: "Unknown error"))
                                )
                            }
                        )
                    }
                }

                // GET /api/matchmaking/status - Get matchmaking status
                route("/status") {
                    install(NotarizedRoute()) {
                        tags = setOf("Matchmaking")
                        get = GetInfo.builder {
                            summary("Get matchmaking status")
                            description("""
                                Get the current matchmaking status for the authenticated player.

                                Possible statuses:
                                - WAITING: Player is in queue, waiting for an opponent
                                - MATCHED: Player has been matched, game created
                                - NOT_FOUND: Player is neither in queue nor matched

                                When MATCHED, the response includes:
                                - gameId: ID of the created game
                                - yourColor: WHITE or BLACK (randomly assigned)

                                Clients should poll this endpoint periodically (e.g., every 2-3 seconds)
                                to check for match updates.
                            """.trimIndent())
                            security = mapOf("JWT" to listOf())
                            response {
                                responseCode(HttpStatusCode.OK)
                                responseType<MatchmakingStatusDTO>()
                                description("Current matchmaking status")
                            }
                            canRespond {
                                responseCode(HttpStatusCode.Unauthorized)
                                responseType<Map<String, String>>()
                                description("Missing or invalid JWT token")
                            }
                        }
                    }

                    get {
                        // Extract user ID from JWT token
                        val principal = call.principal<JWTPrincipal>()
                            ?: return@get call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to "Missing authentication")
                            )

                        val userId = JwtConfig.extractUserId(principal.payload)

                        // Execute get match status use case
                        val matchmakingResult = getMatchStatusUseCase.execute(userId)

                        call.respond(HttpStatusCode.OK, matchmakingResult.toDTO())
                    }
                }
            }
        }
    }
}
