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
import com.gchess.shared.domain.model.PlayerId

/**
 * Domain model representing a chess game.
 *
 * Invariants:
 * - whitePlayer always controls the WHITE side
 * - blackPlayer always controls the BLACK side
 * - currentPlayer is derived from currentSide (no duplication)
 * - Players cannot change sides during a game
 */
data class Game(
    val id: GameId,
    val whitePlayer: PlayerId,
    val blackPlayer: PlayerId,
    val board: ChessPosition = ChessPosition.initial(),
    val currentSide: PlayerSide = PlayerSide.WHITE,
    val status: GameStatus = GameStatus.IN_PROGRESS,
    val moveHistory: List<Move> = emptyList()
) {
    /**
     * Returns the ID of the player whose turn it is.
     * This is a calculated property derived from currentSide.
     */
    val currentPlayer: PlayerId
        get() = if (currentSide == PlayerSide.WHITE) whitePlayer else blackPlayer

    fun makeMove(move: Move): Game {
        return copy(
            board = board.movePiece(move.from, move.to),
            currentSide = currentSide.opposite(),
            moveHistory = moveHistory + move
        )
    }

    fun isPlayerTurn(playerId: PlayerId): Boolean = currentPlayer == playerId

    fun isFinished(): Boolean = status in listOf(
        GameStatus.CHECKMATE,
        GameStatus.STALEMATE,
        GameStatus.DRAW
    )
}
