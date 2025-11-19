package com.gchess.chess.infrastructure.adapter.driver.dto

import com.gchess.chess.domain.model.*
import kotlinx.serialization.Serializable

@Serializable
data class GameDTO(
    val id: String,
    val whitePlayer: String,
    val blackPlayer: String,
    val board: ChessPositionDTO,
    val currentPlayer: String,
    val currentSide: String,
    val status: String,
    val moveHistory: List<MoveDTO>
)

@Serializable
data class ChessPositionDTO(
    val fen: String
)

@Serializable
data class MoveDTO(
    val from: String,
    val to: String
)

// Extension functions to convert between domain models and DTOs
fun Game.toDTO(): GameDTO = GameDTO(
    id = id.value,
    whitePlayer = whitePlayer.value,
    blackPlayer = blackPlayer.value,
    board = board.toDTO(),
    currentPlayer = currentPlayer.value,
    currentSide = currentSide.name,
    status = status.name,
    moveHistory = moveHistory.map { it.toDTO() }
)

fun ChessPosition.toDTO(): ChessPositionDTO = ChessPositionDTO(
    fen = toFen()
)

fun Move.toDTO(): MoveDTO = MoveDTO(
    from = from.toAlgebraic(),
    to = to.toAlgebraic()
)
