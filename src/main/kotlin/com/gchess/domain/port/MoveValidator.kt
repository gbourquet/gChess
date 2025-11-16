package com.gchess.domain.port

import com.gchess.domain.model.Game
import com.gchess.domain.model.Move

interface MoveValidator {
    fun isValidMove(game: Game, move: Move): Boolean
    fun getLegalMoves(game: Game): List<Move>
}
