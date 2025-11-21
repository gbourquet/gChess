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
