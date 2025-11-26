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
import com.gchess.chess.domain.model.Move
import com.gchess.chess.domain.model.Position
import com.gchess.chess.infrastructure.adapter.driver.dto.GameAuthFailedMessage
import com.gchess.chess.infrastructure.adapter.driver.dto.GameAuthSuccessMessage
import com.gchess.chess.infrastructure.adapter.driver.dto.GameErrorMessage
import com.gchess.chess.infrastructure.adapter.driver.dto.GameStateSyncMessage
import com.gchess.chess.infrastructure.adapter.driver.dto.GameWebSocketMessage
import com.gchess.chess.infrastructure.adapter.driver.dto.MoveAttemptMessage
import com.gchess.chess.infrastructure.adapter.driver.dto.MoveDto
import com.gchess.chess.infrastructure.adapter.driver.dto.MoveRejectedMessage
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.infrastructure.websocket.WebSocketJwtAuth
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

/**
 * Configure all WebSocket routes.
 * Phase 1 implementation: Basic infrastructure with authentication and connection management.
 */
fun Application.configureGameWebSocketRoutes() {
    val logger = LoggerFactory.getLogger("GameWebSocketRoutes")
    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    // Inject connection managers
    val gameManager by inject<GameConnectionManager>()
    val spectatorManager by inject<SpectatorConnectionManager>()

    // Inject use cases
    val getGameUseCase by inject<GetGameUseCase>()
    val makeMoveUseCase by inject<MakeMoveUseCase>()

    routing {
        // ========== Game WebSocket ==========
        webSocket("/ws/game/{gameId}") {
            val gameIdParam = call.parameters["gameId"]
            if (gameIdParam == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "gameId required"))
                return@webSocket
            }

            val gameId = try {
                GameId.fromString(gameIdParam)
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid gameId"))
                return@webSocket
            }

            logger.info("New WebSocket connection attempt on /ws/game/$gameId")

            // Authenticate
            val userId = WebSocketJwtAuth.authenticate(
                call = call,
                session = this,
                onSuccess = { userId ->
                    val message = GameAuthSuccessMessage(userId = userId.toString())
                    val jsonMessage = json.encodeToString(GameWebSocketMessage.serializer(), message)
                    send(Frame.Text(jsonMessage))
                },
                onFailure = { reason ->
                    val message = GameAuthFailedMessage(reason = reason)
                    val jsonMessage = json.encodeToString(GameWebSocketMessage.serializer(), message)
                    send(Frame.Text(jsonMessage))
                }
            )
            if (userId == null) {
                logger.warn("Authentication failed, closing connection")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication required"))
                return@webSocket
            }

            // Load the game
            val game = getGameUseCase.execute(gameId)
            if (game == null) {
                logger.warn("Game $gameId not found for user $userId")
                close(CloseReason(CloseReason.Codes.NORMAL, "Game not found"))
                return@webSocket
            }

            // Find the player corresponding to this userId
            val player = when {
                game.whitePlayer.userId == userId -> game.whitePlayer
                game.blackPlayer.userId == userId -> game.blackPlayer
                else -> {
                    logger.warn("User $userId is not a participant in game $gameId")
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not a participant"))
                    return@webSocket
                }
            }

            val playerId = player.id

            // Register connection with playerId
            gameManager.register(playerId, this)
            logger.info("Player $playerId (user $userId) connected to game $gameId")

            // Send initial game state sync (Phase 3 will implement full state)
            val stateSyncMsg = GameStateSyncMessage(
                gameId = gameId.toString(),
                positionFen = game.board.toFen(),
                moveHistory = game.moveHistory.map { move ->
                    MoveDto(
                        from = move.from.toAlgebraic(),
                        to = move.to.toAlgebraic(),
                        promotion = move.promotion?.toString()
                    )
                },
                gameStatus = game.status.toString(),
                currentSide = game.currentSide.toString(),
                whitePlayerId = game.whitePlayer.id.toString(),
                blackPlayerId = game.blackPlayer.id.toString()
            )
            gameManager.send(playerId, stateSyncMsg)

            try {
                // Listen for incoming messages
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        logger.debug("Received message from player $playerId: $text")

                        try {
                            // Parse message
                            val message = json.decodeFromString<GameWebSocketMessage>(text)

                            when (message) {
                                is MoveAttemptMessage -> {
                                    logger.info("Player $playerId attempting move: ${message.from} → ${message.to}")

                                    // Parse positions
                                    val from = Position.fromAlgebraic(message.from)
                                    val to = Position.fromAlgebraic(message.to)
                                    val promotion = message.promotion?.let {
                                        com.gchess.chess.domain.model.PieceType.valueOf(it)
                                    }

                                    val move = Move(from, to, promotion)

                                    // Call the use case (player object already available from connection)
                                    val result = makeMoveUseCase.execute(gameId, player, move)

                                    if (result.isFailure) {
                                        // Send error notification to the player who attempted the move
                                        val error = result.exceptionOrNull()!!
                                        val errorMsg = MoveRejectedMessage(
                                            reason = error.message ?: "Invalid move"
                                        )
                                        gameManager.send(playerId, errorMsg)
                                        logger.warn("Move rejected for player $playerId: ${error.message}")
                                    } else {
                                        // Success - notification already sent by the use case via GameEventNotifier
                                        logger.info("Move executed for player $playerId in game $gameId")
                                    }
                                }

                                else -> {
                                    logger.warn("Unknown message type received from player $playerId: ${message.type}")
                                    val errorMsg = GameErrorMessage(
                                        code = "UNKNOWN_MESSAGE_TYPE",
                                        message = "Unknown message type: ${message.type}"
                                    )
                                    gameManager.send(playerId, errorMsg)
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Error parsing message from player $playerId", e)
                            val errorMsg = GameErrorMessage(
                                code = "INVALID_MESSAGE",
                                message = "Failed to parse message: ${e.message}"
                            )
                            gameManager.send(playerId, errorMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in game WebSocket for player $playerId", e)
            } finally {
                // Unregister on disconnection
                gameManager.unregister(playerId)
                logger.info("Player $playerId disconnected from game $gameId")

                // Notify the other player (and spectators)
                // Reload game to get fresh state
                val currentGame = getGameUseCase.execute(gameId)
                if (currentGame != null) {
                    // Note: GameEventNotifier should be injected here, but we can't access it from routes
                    // For now, we just log. In a production system, we'd need to refactor this.
                    logger.info("Player $playerId disconnected, should notify opponent")
                    // TODO: Notify via GameEventNotifier (requires injection in routes)
                }
            }
        }

        // ========== Spectator WebSocket ==========
        webSocket("/ws/game/{gameId}/spectate") {
            val gameIdParam = call.parameters["gameId"]
            if (gameIdParam == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "gameId required"))
                return@webSocket
            }

            val gameId = try {
                GameId.fromString(gameIdParam)
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid gameId"))
                return@webSocket
            }

            logger.info("New spectator WebSocket connection attempt on /ws/game/$gameId/spectate")

            // Authenticate
            val userId = WebSocketJwtAuth.authenticate(
                call = call,
                session = this,
                onSuccess = { userId ->
                    val message = GameAuthSuccessMessage(userId = userId.toString())
                    val jsonMessage = json.encodeToString(GameWebSocketMessage.serializer(), message)
                    send(Frame.Text(jsonMessage))
                },
                onFailure = { reason ->
                    val message = GameAuthFailedMessage(reason = reason)
                    val jsonMessage = json.encodeToString(GameWebSocketMessage.serializer(), message)
                    send(Frame.Text(jsonMessage))
                }
            )
            if (userId == null) {
                logger.warn("Authentication failed, closing connection")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication required"))
                return@webSocket
            }

            // Verify the game exists
            val game = getGameUseCase.execute(gameId)
            if (game == null) {
                logger.warn("Game $gameId not found for spectator $userId")
                close(CloseReason(CloseReason.Codes.NORMAL, "Game not found"))
                return@webSocket
            }

            // Register spectator
            spectatorManager.register(gameId, userId, this)
            logger.info("User $userId joined as spectator for game $gameId")

            // Send initial game state sync
            val stateSyncMsg = GameStateSyncMessage(
                gameId = gameId.toString(),
                positionFen = game.board.toFen(),
                moveHistory = game.moveHistory.map { move ->
                    MoveDto(
                        from = move.from.toAlgebraic(),
                        to = move.to.toAlgebraic(),
                        promotion = move.promotion?.toString()
                    )
                },
                gameStatus = game.status.toString(),
                currentSide = game.currentSide.toString(),
                whitePlayerId = game.whitePlayer.id.toString(),
                blackPlayerId = game.blackPlayer.id.toString()
            )
            spectatorManager.broadcastToSpectators(gameId, stateSyncMsg)

            try {
                // Spectators are read-only, but we still need to keep the connection alive
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        logger.debug("Spectator $userId sent message (ignored): ${frame.readText()}")
                        // Spectators cannot send messages (read-only)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in spectator WebSocket for user $userId", e)
            } finally {
                // Unregister on disconnection
                spectatorManager.unregister(gameId, userId)
                logger.info("Spectator $userId disconnected from game $gameId")
            }
        }
    }
}
