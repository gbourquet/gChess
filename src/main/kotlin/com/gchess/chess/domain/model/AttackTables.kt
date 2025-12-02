package com.gchess.chess.domain.model

/**
 * Pre-computed attack tables for fast bitboard-based attack detection.
 *
 * These tables allow O(1) lookup for knight and king attacks,
 * dramatically speeding up check detection.
 */
internal object AttackTables {

    /**
     * Knight attack table: for each square (0-63), bitboard of squares a knight can attack.
     * Knights move in an L-shape: 2 squares in one direction, 1 in perpendicular.
     */
    val KNIGHT_ATTACKS: LongArray = LongArray(64) { square ->
        val file = square % 8
        val rank = square / 8
        var attacks = 0L

        // All 8 possible knight moves (2 up/down + 1 left/right, or vice versa)
        val moves = arrayOf(
            Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2),
            Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
        )

        for ((df, dr) in moves) {
            val newFile = file + df
            val newRank = rank + dr
            if (newFile in 0..7 && newRank in 0..7) {
                val targetSquare = newRank * 8 + newFile
                attacks = attacks or (1L shl targetSquare)
            }
        }

        attacks
    }

    /**
     * King attack table: for each square (0-63), bitboard of squares a king can attack.
     * Kings move 1 square in any direction (8 directions).
     */
    val KING_ATTACKS: LongArray = LongArray(64) { square ->
        val file = square % 8
        val rank = square / 8
        var attacks = 0L

        // All 8 directions (horizontal, vertical, diagonal)
        val moves = arrayOf(
            Pair(-1, -1), Pair(-1, 0), Pair(-1, 1),
            Pair(0, -1), Pair(0, 1),
            Pair(1, -1), Pair(1, 0), Pair(1, 1)
        )

        for ((df, dr) in moves) {
            val newFile = file + df
            val newRank = rank + dr
            if (newFile in 0..7 && newRank in 0..7) {
                val targetSquare = newRank * 8 + newFile
                attacks = attacks or (1L shl targetSquare)
            }
        }

        attacks
    }

    /**
     * Pawn attack table: for each square (0-63) and each side, bitboard of squares a pawn can attack.
     * White pawns attack diagonally upward, black pawns attack diagonally downward.
     */
    val WHITE_PAWN_ATTACKS: LongArray = LongArray(64) { square ->
        val file = square % 8
        val rank = square / 8
        var attacks = 0L

        // White pawns attack up-left and up-right
        if (rank < 7) { // Not on 8th rank
            if (file > 0) attacks = attacks or (1L shl ((rank + 1) * 8 + file - 1)) // Up-left
            if (file < 7) attacks = attacks or (1L shl ((rank + 1) * 8 + file + 1)) // Up-right
        }

        attacks
    }

    val BLACK_PAWN_ATTACKS: LongArray = LongArray(64) { square ->
        val file = square % 8
        val rank = square / 8
        var attacks = 0L

        // Black pawns attack down-left and down-right
        if (rank > 0) { // Not on 1st rank
            if (file > 0) attacks = attacks or (1L shl ((rank - 1) * 8 + file - 1)) // Down-left
            if (file < 7) attacks = attacks or (1L shl ((rank - 1) * 8 + file + 1)) // Down-right
        }

        attacks
    }

    /**
     * Generates slider attacks (rook-like) from a square to occupied squares.
     * Uses classical approach (ray-casting).
     *
     * @param square The starting square (0-63)
     * @param occupied Bitboard of all occupied squares
     * @param directions List of (fileDir, rankDir) tuples for rays
     * @return Bitboard of all attacked squares
     */
    fun generateSliderAttacks(square: Int, occupied: Long, directions: List<Pair<Int, Int>>): Long {
        val file = square % 8
        val rank = square / 8
        var attacks = 0L

        for ((df, dr) in directions) {
            var f = file + df
            var r = rank + dr

            while (f in 0..7 && r in 0..7) {
                val targetSquare = r * 8 + f
                val targetBit = 1L shl targetSquare
                attacks = attacks or targetBit

                // Stop if we hit an occupied square (blocker)
                if ((occupied and targetBit) != 0L) break

                f += df
                r += dr
            }
        }

        return attacks
    }

    /**
     * Rook attack directions (4 rays: horizontal and vertical).
     */
    val ROOK_DIRECTIONS = listOf(
        Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
    )

    /**
     * Bishop attack directions (4 rays: diagonals).
     */
    val BISHOP_DIRECTIONS = listOf(
        Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1)
    )

    /**
     * Queen attack directions (8 rays: rook + bishop).
     */
    val QUEEN_DIRECTIONS = ROOK_DIRECTIONS + BISHOP_DIRECTIONS
}