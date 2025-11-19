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
