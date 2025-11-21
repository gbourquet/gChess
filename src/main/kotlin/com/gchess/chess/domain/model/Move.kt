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

data class Move(
    val from: Position,
    val to: Position,
    val promotion: PieceType? = null
) {
    fun toAlgebraic(): String {
        val base = "${from.toAlgebraic()}${to.toAlgebraic()}"
        return promotion?.let { base + it.name.first().lowercaseChar() } ?: base
    }

    companion object {
        fun fromAlgebraic(notation: String): Move {
            require(notation.length >= 4) { "Invalid move notation" }
            val from = Position.fromAlgebraic(notation.substring(0, 2))
            val to = Position.fromAlgebraic(notation.substring(2, 4))
            val promotion = if (notation.length == 5) {
                when (notation[4].lowercaseChar()) {
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> null
                }
            } else null
            return Move(from, to, promotion)
        }
    }
}
