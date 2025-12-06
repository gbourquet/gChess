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

import com.gchess.shared.domain.model.PlayerSide
import kotlin.random.Random

/**
 * Zobrist hashing for chess positions.
 * Provides fast hash computation for Transposition Table lookup.
 *
 * Hash components:
 * - Piece on square: pieceKeys[piece][square]
 * - Side to move: sideToMoveKey
 * - Castling rights: castlingKeys[index]
 * - En passant: enPassantKeys[file]
 */
object ZobristHash {
    // 12 piece types (6 types * 2 colors) * 64 squares
    private val pieceKeys = Array(12) { LongArray(64) }

    // Side to move (white or black)
    private var sideToMoveKey: Long = 0L

    // Castling rights (4 bits: WK, WQ, BK, BQ)
    private val castlingKeys = LongArray(16)

    // En passant file (0-7, or -1 for none)
    private val enPassantKeys = LongArray(8)

    init {
        // Initialize with pseudo-random numbers (deterministic seed for consistency)
        val random = Random(42)

        // Piece keys
        for (piece in 0 until 12) {
            for (square in 0 until 64) {
                pieceKeys[piece][square] = random.nextLong()
            }
        }

        // Side to move
        sideToMoveKey = random.nextLong()

        // Castling rights
        for (i in 0 until 16) {
            castlingKeys[i] = random.nextLong()
        }

        // En passant
        for (file in 0 until 8) {
            enPassantKeys[file] = random.nextLong()
        }
    }

    /**
     * Compute full Zobrist hash for a position.
     * This is slow - prefer incremental updates when possible.
     */
    fun compute(position: ChessPosition): Long {
        var hash = 0L

        // Hash all pieces
        val whitePieces = position.whitePieces()
        val blackPieces = position.blackPieces()

        for (square in 0 until 64) {
            val bit = 1L shl square

            // Check each piece type
            if (whitePieces and bit != 0L) {
                // White piece
                when {
                    position.whitePawns and bit != 0L -> hash = hash xor pieceKeys[0][square]
                    position.whiteKnights and bit != 0L -> hash = hash xor pieceKeys[1][square]
                    position.whiteBishops and bit != 0L -> hash = hash xor pieceKeys[2][square]
                    position.whiteRooks and bit != 0L -> hash = hash xor pieceKeys[3][square]
                    position.whiteQueens and bit != 0L -> hash = hash xor pieceKeys[4][square]
                    position.whiteKings and bit != 0L -> hash = hash xor pieceKeys[5][square]
                }
            } else if (blackPieces and bit != 0L) {
                // Black piece
                when {
                    position.blackPawns and bit != 0L -> hash = hash xor pieceKeys[6][square]
                    position.blackKnights and bit != 0L -> hash = hash xor pieceKeys[7][square]
                    position.blackBishops and bit != 0L -> hash = hash xor pieceKeys[8][square]
                    position.blackRooks and bit != 0L -> hash = hash xor pieceKeys[9][square]
                    position.blackQueens and bit != 0L -> hash = hash xor pieceKeys[10][square]
                    position.blackKings and bit != 0L -> hash = hash xor pieceKeys[11][square]
                }
            }
        }

        // Hash side to move
        if (position.sideToMove == PlayerSide.BLACK) {
            hash = hash xor sideToMoveKey
        }

        // Hash castling rights
        val castlingIndex = encodeCastlingRights(position.castlingRights)
        hash = hash xor castlingKeys[castlingIndex]

        // Hash en passant
        position.enPassantSquare?.let { enPassantAlgebraic ->
            val file = enPassantAlgebraic[0] - 'a'
            hash = hash xor enPassantKeys[file]
        }

        return hash
    }

    /**
     * Get piece key for incremental hash update.
     */
    fun getPieceKey(pieceType: PieceType, side: PlayerSide, square: Int): Long {
        val pieceIndex = when (side) {
            PlayerSide.WHITE -> when (pieceType) {
                PieceType.PAWN -> 0
                PieceType.KNIGHT -> 1
                PieceType.BISHOP -> 2
                PieceType.ROOK -> 3
                PieceType.QUEEN -> 4
                PieceType.KING -> 5
            }
            PlayerSide.BLACK -> when (pieceType) {
                PieceType.PAWN -> 6
                PieceType.KNIGHT -> 7
                PieceType.BISHOP -> 8
                PieceType.ROOK -> 9
                PieceType.QUEEN -> 10
                PieceType.KING -> 11
            }
        }
        return pieceKeys[pieceIndex][square]
    }

    /**
     * Get side to move key for incremental hash update.
     */
    fun getSideToMoveKey(): Long = sideToMoveKey

    /**
     * Get castling key for incremental hash update.
     */
    fun getCastlingKey(castlingRights: CastlingRights): Long {
        val index = encodeCastlingRights(castlingRights)
        return castlingKeys[index]
    }

    /**
     * Get en passant key for incremental hash update.
     */
    fun getEnPassantKey(file: Int): Long = enPassantKeys[file]

    /**
     * Encode castling rights to 4-bit index.
     */
    private fun encodeCastlingRights(rights: CastlingRights): Int {
        var index = 0
        if (rights.whiteKingSide) index = index or 8
        if (rights.whiteQueenSide) index = index or 4
        if (rights.blackKingSide) index = index or 2
        if (rights.blackQueenSide) index = index or 1
        return index
    }
}
