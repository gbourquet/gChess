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
package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.matchmaking.infrastructure.adapter.driver.MatchmakingConnectionManager
import com.gchess.matchmaking.domain.model.Match
import com.gchess.matchmaking.domain.port.MatchmakingNotifier
import com.gchess.matchmaking.infrastructure.adapter.driver.dto.MatchFoundMessage
import com.gchess.matchmaking.infrastructure.adapter.driver.dto.MatchmakingErrorMessage
import com.gchess.matchmaking.infrastructure.adapter.driver.dto.QueuePositionUpdateMessage
import com.gchess.shared.domain.model.UserId
import org.slf4j.LoggerFactory

/**
 * WebSocket implementation of MatchmakingNotifier.
 *
 * Sends real-time notifications to users via WebSocket connections
 * managed by MatchmakingConnectionManager.
 *
 * Note: Notifications are best-effort. Failures are logged but do not
 * stop the matchmaking process.
 */
class WebSocketMatchmakingNotifier(
    private val connectionManager: MatchmakingConnectionManager
) : MatchmakingNotifier {
    private val logger = LoggerFactory.getLogger(WebSocketMatchmakingNotifier::class.java)

    override suspend fun notifyQueuePosition(userId: UserId, position: Int) {
        try {
            val message = QueuePositionUpdateMessage(position = position)
            val sent = connectionManager.send(userId, message)

            if (sent) {
                logger.debug("Sent queue position $position to user $userId")
            } else {
                logger.warn("Failed to send queue position to user $userId: not connected")
            }
        } catch (e: Exception) {
            logger.error("Error sending queue position notification to user $userId", e)
        }
    }

    override suspend fun notifyMatchFound(match: Match) {
        try {
            // Send MatchFound to white player
            val whiteMessage = MatchFoundMessage(
                gameId = match.gameId.toString(),
                yourColor = "WHITE",
                playerId = match.whitePlayer.id.value, // Will be populated by the client from GameStateSync
                opponentUserId = match.blackPlayer.userId.value
            )
            val whiteSent = connectionManager.send(match.whitePlayer.userId, whiteMessage)

            // Send MatchFound to black player
            val blackMessage = MatchFoundMessage(
                gameId = match.gameId.toString(),
                yourColor = "BLACK",
                playerId = match.blackPlayer.id.value, // Will be populated by the client from GameStateSync
                opponentUserId = match.whitePlayer.userId.value
            )
            val blackSent = connectionManager.send(match.blackPlayer.userId, blackMessage)

            if (whiteSent && blackSent) {
                logger.info("Match found notification sent to both players (game ${match.gameId})")
            } else {
                logger.warn(
                    "Match found but notification failed: white=$whiteSent, black=$blackSent (game ${match.gameId})"
                )
            }

            // Unregister both users from matchmaking connections
            // They should now connect to /ws/game/{gameId}
            connectionManager.unregister(match.whitePlayer.userId)
            connectionManager.unregister(match.blackPlayer.userId)

            logger.debug("Unregistered both players from matchmaking connections")
        } catch (e: Exception) {
            logger.error("Error sending match found notification for game ${match.gameId}", e)
        }
    }

    override suspend fun notifyError(userId: UserId, errorCode: String, message: String) {
        try {
            val errorMessage = MatchmakingErrorMessage(
                code = errorCode,
                message = message
            )
            val sent = connectionManager.send(userId, errorMessage)

            if (sent) {
                logger.debug("Sent error notification to user $userId: $errorCode")
            } else {
                logger.warn("Failed to send error notification to user $userId: not connected")
            }
        } catch (e: Exception) {
            logger.error("Error sending error notification to user $userId", e)
        }
    }
}
