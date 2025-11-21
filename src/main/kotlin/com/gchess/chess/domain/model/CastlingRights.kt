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

/**
 * Value Object representing castling rights for both players.
 *
 * In chess, a player can castle if:
 * - The king and the relevant rook have not moved
 * - There are no pieces between the king and rook
 * - The king is not in check, doesn't pass through check, and doesn't end in check
 *
 * This class tracks only the first condition (whether pieces have moved).
 */
data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true
) {
    /**
     * Checks if the given side can castle kingside (petit roque)
     */
    fun canCastleKingside(side: PlayerSide): Boolean = when (side) {
        PlayerSide.WHITE -> whiteKingSide
        PlayerSide.BLACK -> blackKingSide
    }

    /**
     * Checks if the given side can castle queenside (grand roque)
     */
    fun canCastleQueenside(side: PlayerSide): Boolean = when (side) {
        PlayerSide.WHITE -> whiteQueenSide
        PlayerSide.BLACK -> blackQueenSide
    }

    /**
     * Returns castling rights with both rights removed for the given side
     * (typically called when the king moves)
     */
    fun withoutRightsFor(side: PlayerSide): CastlingRights = when (side) {
        PlayerSide.WHITE -> copy(whiteKingSide = false, whiteQueenSide = false)
        PlayerSide.BLACK -> copy(blackKingSide = false, blackQueenSide = false)
    }

    /**
     * Returns castling rights with kingside right removed for the given side
     */
    fun withoutKingsideFor(side: PlayerSide): CastlingRights = when (side) {
        PlayerSide.WHITE -> copy(whiteKingSide = false)
        PlayerSide.BLACK -> copy(blackKingSide = false)
    }

    /**
     * Returns castling rights with queenside right removed for the given side
     */
    fun withoutQueensideFor(side: PlayerSide): CastlingRights = when (side) {
        PlayerSide.WHITE -> copy(whiteQueenSide = false)
        PlayerSide.BLACK -> copy(blackQueenSide = false)
    }

    /**
     * Converts castling rights to FEN notation string
     * Format: KQkq (White kingside, White queenside, Black kingside, Black queenside)
     * Returns "-" if no castling rights available
     */
    fun toFenString(): String = buildString {
        if (whiteKingSide) append('K')
        if (whiteQueenSide) append('Q')
        if (blackKingSide) append('k')
        if (blackQueenSide) append('q')
    }.ifEmpty { "-" }

    companion object {
        /**
         * No castling rights available
         */
        val NONE = CastlingRights(
            whiteKingSide = false,
            whiteQueenSide = false,
            blackKingSide = false,
            blackQueenSide = false
        )

        /**
         * All castling rights available (initial position)
         */
        val ALL = CastlingRights()

        /**
         * Parses castling rights from FEN notation
         * @param fen FEN castling rights string (e.g., "KQkq", "Kq", "-")
         * @return CastlingRights object
         */
        fun fromFenString(fen: String): CastlingRights {
            if (fen == "-") return NONE

            return CastlingRights(
                whiteKingSide = fen.contains('K'),
                whiteQueenSide = fen.contains('Q'),
                blackKingSide = fen.contains('k'),
                blackQueenSide = fen.contains('q')
            )
        }
    }
}
