package com.gchess.infrastructure.adapter.output

import com.gchess.domain.model.Game
import com.gchess.domain.model.Move
import com.gchess.domain.port.MoveValidator

class BasicMoveValidator : MoveValidator {
    override fun isValidMove(game: Game, move: Move): Boolean {
        // TODO: Implement proper chess move validation
        // For now, just check basic conditions
        val piece = game.board.pieceAt(move.from) ?: return false

        // Check if it's the correct player's turn
        if (piece.color != game.currentPlayer) return false

        // Check if destination is valid
        if (!move.to.isValid()) return false

        // Check if destination doesn't have piece of same color
        val destinationPiece = game.board.pieceAt(move.to)
        if (destinationPiece?.color == piece.color) return false

        return true
    }

    override fun getLegalMoves(game: Game): List<Move> {
        // TODO: Implement proper legal move generation
        return emptyList()
    }
}
