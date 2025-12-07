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
package com.gchess.chess.domain.port

import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.model.Move
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerId

/**
 * Port for sending real-time notifications during gameplay.
 *
 * This abstraction allows the domain to send game event notifications
 * without depending on specific infrastructure (WebSocket, SSE, etc.).
 *
 * Implementation note:
 * - The infrastructure layer provides the concrete implementation (e.g., WebSocket)
 * - Notifications are best-effort: failures should be logged but not stop the game
 * - All notifications are broadcast to both players and spectators
 */
interface GameEventNotifier {
    /**
     * Notify that a move has been executed successfully.
     * This should be broadcast to both players and all spectators.
     *
     * @param game The game state after the move
     * @param move The move that was executed
     */
    suspend fun notifyMoveExecuted(game: Game, move: Move)

    /**
     * Notify a specific player that their move was rejected.
     * This is sent only to the player who attempted the invalid move.
     *
     * @param playerId The player who attempted the move
     * @param reason The reason for rejection
     */
    suspend fun notifyMoveRejected(playerId: PlayerId, reason: String)

    /**
     * Notify that a player has disconnected.
     * This should be broadcast to the other player and all spectators.
     *
     * @param game The current game
     * @param playerId The player who disconnected
     */
    suspend fun notifyPlayerDisconnected(game: Game, playerId: PlayerId)

    /**
     * Notify that a player has reconnected.
     * This should be broadcast to the other player and all spectators.
     *
     * @param game The current game
     * @param playerId The player who reconnected
     */
    suspend fun notifyPlayerReconnected(game: Game, playerId: PlayerId)

    /**
     * Notify that a player has resigned from the game.
     * This should be broadcast to both players and all spectators.
     *
     * @param game The game state after resignation
     * @param player The player who resigned
     */
    suspend fun notifyGameResigned(game: Game, player: Player)

    /**
     * Notify that a player has offered a draw.
     * This should be sent to the opponent.
     *
     * @param game The current game state
     * @param player The player offering the draw
     */
    suspend fun notifyDrawOffered(game: Game, player: Player)

    /**
     * Notify that a draw offer has been accepted.
     * This should be broadcast to both players and all spectators.
     *
     * @param game The game state after accepting the draw
     * @param player The player who accepted the draw
     */
    suspend fun notifyDrawAccepted(game: Game, player: Player)

    /**
     * Notify that a draw offer has been rejected.
     * This should be sent to the player who offered the draw.
     *
     * @param game The current game state
     * @param player The player who rejected the draw
     */
    suspend fun notifyDrawRejected(game: Game, player: Player)
}
