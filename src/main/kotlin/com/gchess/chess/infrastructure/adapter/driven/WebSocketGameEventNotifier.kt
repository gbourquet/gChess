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
package com.gchess.chess.infrastructure.adapter.driven

import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.model.GameStatus
import com.gchess.chess.domain.model.Move
import com.gchess.chess.domain.port.GameEventNotifier
import com.gchess.chess.infrastructure.adapter.driver.dto.MoveDto
import com.gchess.chess.infrastructure.adapter.driver.dto.MoveExecutedMessage
import com.gchess.chess.infrastructure.adapter.driver.dto.MoveRejectedMessage
import com.gchess.chess.infrastructure.adapter.driver.dto.PlayerDisconnectedMessage
import com.gchess.chess.infrastructure.adapter.driver.dto.PlayerReconnectedMessage
import com.gchess.chess.infrastructure.adapter.driver.GameConnectionManager
import com.gchess.chess.infrastructure.adapter.driver.SpectatorConnectionManager
import com.gchess.shared.domain.model.PlayerId
import org.slf4j.LoggerFactory

import com.gchess.chess.domain.service.ChessRules

/**
 * WebSocket implementation of GameEventNotifier.
 *
 * Sends real-time notifications to players and spectators via WebSocket connections
 * managed by GameConnectionManager and SpectatorConnectionManager.
 *
 * Note: Notifications are best-effort. Failures are logged but do not
 * stop the game logic.
 */
class WebSocketGameEventNotifier(
    private val gameConnectionManager: GameConnectionManager,
    private val spectatorConnectionManager: SpectatorConnectionManager,
    private val chessRules: ChessRules
) : GameEventNotifier {
    private val logger = LoggerFactory.getLogger(WebSocketGameEventNotifier::class.java)

    override suspend fun notifyMoveExecuted(game: Game, move: Move) {
        try {
            val message = MoveExecutedMessage(
                move = MoveDto(
                    from = move.from.toAlgebraic(),
                    to = move.to.toAlgebraic(),
                    promotion = move.promotion?.toString()
                ),
                newPositionFen = game.board.toFen(),
                gameStatus = game.status.toString(),
                currentSide = game.currentSide.toString(),
                isCheck = chessRules.isInCheck(game.board, game.currentSide)
            )

            // Broadcast to both players
            val (whiteSent, blackSent) = gameConnectionManager.broadcastToGame(game, message)

            // Broadcast to all spectators
            val spectatorCount = spectatorConnectionManager.broadcastToSpectators(game.id, message)

            logger.info(
                "Move executed notification sent for game ${game.id}: " +
                        "white=$whiteSent, black=$blackSent, spectators=$spectatorCount"
            )

            // Close WebSocket connections if game has ended
            if (isGameFinished(game.status)) {
                logger.info("Game ${game.id} finished with status ${game.status}, closing WebSocket connections")

                // Give clients a moment to process the final move before closing
                kotlinx.coroutines.delay(1000)

                // Close player connections
                val (whiteClosed, blackClosed) = gameConnectionManager.closeGameConnections(game)

                // Close spectator connections
                val spectatorsClosed = spectatorConnectionManager.closeGameSpectators(game.id)

                logger.info(
                    "Closed WebSocket connections for finished game ${game.id}: " +
                            "white=$whiteClosed, black=$blackClosed, spectators=$spectatorsClosed"
                )
            }
        } catch (e: Exception) {
            logger.error("Error sending move executed notification for game ${game.id}", e)
        }
    }

    /**
     * Check if a game has finished (terminal state).
     */
    private fun isGameFinished(status: GameStatus): Boolean {
        return status == GameStatus.CHECKMATE ||
                status == GameStatus.STALEMATE ||
                status == GameStatus.DRAW
    }

    override suspend fun notifyMoveRejected(playerId: PlayerId, reason: String) {
        try {
            val message = MoveRejectedMessage(reason = reason)
            val sent = gameConnectionManager.send(playerId, message)

            if (sent) {
                logger.debug("Move rejected notification sent to player $playerId")
            } else {
                logger.warn("Failed to send move rejected notification to player $playerId: not connected")
            }
        } catch (e: Exception) {
            logger.error("Error sending move rejected notification to player $playerId", e)
        }
    }

    override suspend fun notifyPlayerDisconnected(game: Game, playerId: PlayerId) {
        try {
            val message = PlayerDisconnectedMessage(playerId = playerId.toString())

            // Broadcast to the other player
            val opponentId = if (playerId == game.whitePlayer.id) {
                game.blackPlayer.id
            } else {
                game.whitePlayer.id
            }
            val opponentSent = gameConnectionManager.send(opponentId, message)

            // Broadcast to all spectators
            val spectatorCount = spectatorConnectionManager.broadcastToSpectators(game.id, message)

            logger.info(
                "Player disconnected notification sent for game ${game.id}: " +
                        "opponent=$opponentSent, spectators=$spectatorCount"
            )
        } catch (e: Exception) {
            logger.error("Error sending player disconnected notification for game ${game.id}", e)
        }
    }

    override suspend fun notifyPlayerReconnected(game: Game, playerId: PlayerId) {
        try {
            val message = PlayerReconnectedMessage(playerId = playerId.toString())

            // Broadcast to the other player
            val opponentId = if (playerId == game.whitePlayer.id) {
                game.blackPlayer.id
            } else {
                game.whitePlayer.id
            }
            val opponentSent = gameConnectionManager.send(opponentId, message)

            // Broadcast to all spectators
            val spectatorCount = spectatorConnectionManager.broadcastToSpectators(game.id, message)

            logger.info(
                "Player reconnected notification sent for game ${game.id}: " +
                        "opponent=$opponentSent, spectators=$spectatorCount"
            )
        } catch (e: Exception) {
            logger.error("Error sending player reconnected notification for game ${game.id}", e)
        }
    }
}
