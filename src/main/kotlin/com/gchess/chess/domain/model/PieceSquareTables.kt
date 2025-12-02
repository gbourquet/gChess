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
 * Piece-Square Tables for positional evaluation.
 *
 * These tables assign bonus/penalty values to each square for each piece type.
 * Encourages pieces to occupy strong squares and discourages weak placements.
 *
 * Tables are from White's perspective (rank 0 = 1st rank for white).
 * For black pieces, the table is flipped vertically.
 *
 * Values are in centipawns (1/100th of a pawn).
 *
 * Based on simplified Stockfish piece-square tables.
 */
object PieceSquareTables {

    /**
     * Pawn table: encourages central pawns and advancement.
     * Heavily penalizes wing pawns, strongly rewards central control.
     */
    private val PAWN = intArrayOf(
        //  a1   b1   c1   d1   e1   f1   g1   h1
            0,   0,   0,   0,   0,   0,   0,   0, // rank 1 (impossible)
        // a2   b2   c2   d2   e2   f2   g2   h2
          -10, -10,  -5,   5,   5,  -5, -10, -10, // rank 2 (start position)
        // a3   b3   c3   d3   e3   f3   g3   h3
          -15, -15,  -5,   5,   5,  -5, -15, -15, // rank 3
        // a4   b4   c4   d4   e4   f4   g4   h4
          -10,  -5,   5,  40,  40,   5,  -5, -10, // rank 4 (central control!)
        // a5   b5   c5   d5   e5   f5   g5   h5
            0,   5,  15,  50,  50,  15,   5,   0, // rank 5 (advanced center)
        // a6   b6   c6   d6   e6   f6   g6   h6
           10,  15,  25,  60,  60,  25,  15,  10, // rank 6 (very advanced)
        // a7   b7   c7   d7   e7   f7   g7   h7
           80,  80,  80,  80,  80,  80,  80,  80, // rank 7 (about to promote)
        // a8   b8   c8   d8   e8   f8   g8   h8
            0,   0,   0,   0,   0,   0,   0,   0  // rank 8 (impossible)
    )

    /**
     * Knight table: encourages central placement, discourages edges.
     * Knights are weak on the rim.
     */
    private val KNIGHT = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20,   0,   0,   0,   0, -20, -40,
        -30,   0,  10,  15,  15,  10,   0, -30,
        -30,   5,  15,  20,  20,  15,   5, -30,
        -30,   0,  15,  20,  20,  15,   0, -30,
        -30,   5,  10,  15,  15,  10,   5, -30,
        -40, -20,   0,   5,   5,   0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )

    /**
     * Bishop table: encourages central placement and long diagonals.
     */
    private val BISHOP = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10,   0,   0,   0,   0,   0,   0, -10,
        -10,   0,   5,  10,  10,   5,   0, -10,
        -10,   5,   5,  10,  10,   5,   5, -10,
        -10,   0,  10,  10,  10,  10,   0, -10,
        -10,  10,  10,  10,  10,  10,  10, -10,
        -10,   5,   0,   0,   0,   0,   5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20
    )

    /**
     * Rook table: encourages 7th rank (attacking enemy pawns).
     */
    private val ROOK = intArrayOf(
          0,   0,   0,   0,   0,   0,   0,   0,
          5,  10,  10,  10,  10,  10,  10,   5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
         -5,   0,   0,   0,   0,   0,   0,  -5,
          0,   0,   0,   5,   5,   0,   0,   0
    )

    /**
     * Queen table: slight preference for center, but queen is strong everywhere.
     */
    private val QUEEN = intArrayOf(
        -20, -10, -10,  -5,  -5, -10, -10, -20,
        -10,   0,   0,   0,   0,   0,   0, -10,
        -10,   0,   5,   5,   5,   5,   0, -10,
         -5,   0,   5,   5,   5,   5,   0,  -5,
          0,   0,   5,   5,   5,   5,   0,  -5,
        -10,   5,   5,   5,   5,   5,   0, -10,
        -10,   0,   5,   0,   0,   0,   0, -10,
        -20, -10, -10,  -5,  -5, -10, -10, -20
    )

    /**
     * King middlegame table: encourages castling and safety.
     * King should stay safe behind pawns.
     */
    private val KING = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
         20,  20,   0,   0,   0,   0,  20,  20,
         20,  30,  10,   0,   0,  10,  30,  20
    )

    /**
     * Get the bonus for a piece at a given square index (0-63).
     *
     * @param pieceType The type of piece
     * @param squareIndex The square index (0-63, where 0=a1, 63=h8)
     * @param isWhite True if piece is white
     * @return Positional bonus in centipawns
     */
    fun getBonus(pieceType: PieceType, squareIndex: Int, isWhite: Boolean): Int {
        // For black pieces, flip the square vertically (mirror across horizontal axis)
        val index = if (isWhite) squareIndex else (squareIndex xor 56)

        return when (pieceType) {
            PieceType.PAWN -> PAWN[index]
            PieceType.KNIGHT -> KNIGHT[index]
            PieceType.BISHOP -> BISHOP[index]
            PieceType.ROOK -> ROOK[index]
            PieceType.QUEEN -> QUEEN[index]
            PieceType.KING -> KING[index]
        }
    }
}
