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
package com.gchess.chess.domain.model

import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerSide

/**
 * Domain model representing a chess game.
 *
 * Invariants:
 * - whitePlayer always controls the WHITE side
 * - blackPlayer always controls the BLACK side
 * - currentPlayer is derived from currentSide (no duplication)
 * - Players cannot change sides during a game
 *
 * Note: This entity does NOT manipulate UserIds directly in its methods.
 * All interactions are through Player objects. The conversion from UserId (from JWT)
 * to Player is done in the infrastructure layer (GameRoutes).
 */
data class Game(
    val id: GameId,
    val whitePlayer: Player,
    val blackPlayer: Player,
    val board: ChessPosition = ChessPosition.initial(),
    val currentSide: PlayerSide = PlayerSide.WHITE,
    val status: GameStatus = GameStatus.IN_PROGRESS,
    val moveHistory: List<Move> = emptyList()
) {
    init {
        require(whitePlayer.side == PlayerSide.WHITE) {
            "White player must have WHITE side"
        }
        require(blackPlayer.side == PlayerSide.BLACK) {
            "Black player must have BLACK side"
        }
    }

    /**
     * Returns the Player whose turn it is.
     * This is a calculated property derived from currentSide.
     */
    val currentPlayer: Player
        get() = if (currentSide == PlayerSide.WHITE) whitePlayer else blackPlayer

    /**
     * Checks if it's the turn of the given player.
     *
     * @param player The player to check
     * @return true if it's this player's turn
     */
    fun isPlayerTurn(player: Player): Boolean = currentPlayer == player

    /**
     * Returns the opponent of the given player.
     *
     * @param player The player whose opponent to find
     * @return The opponent Player
     */
    fun getOpponent(player: Player): Player {
        return if (player.side == PlayerSide.WHITE) blackPlayer else whitePlayer
    }

    /**
     * Makes a move on the board and switches turns.
     *
     * @param move The move to make
     * @return A new Game instance with the move applied
     */
    fun makeMove(move: Move): Game {
        return copy(
            board = board.movePiece(move.from, move.to, move.promotion),
            currentSide = currentSide.opposite(),
            moveHistory = moveHistory + move
        )
    }

    /**
     * Checks if the game is finished.
     *
     * @return true if the game status is CHECKMATE, STALEMATE, or DRAW
     */
    fun isFinished(): Boolean = status in listOf(
        GameStatus.CHECKMATE,
        GameStatus.STALEMATE,
        GameStatus.DRAW
    )
}
