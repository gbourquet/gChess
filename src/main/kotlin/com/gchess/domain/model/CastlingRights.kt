package com.gchess.domain.model

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
     * Checks if the given color can castle kingside (petit roque)
     */
    fun canCastleKingside(color: Color): Boolean = when (color) {
        Color.WHITE -> whiteKingSide
        Color.BLACK -> blackKingSide
    }

    /**
     * Checks if the given color can castle queenside (grand roque)
     */
    fun canCastleQueenside(color: Color): Boolean = when (color) {
        Color.WHITE -> whiteQueenSide
        Color.BLACK -> blackQueenSide
    }

    /**
     * Returns castling rights with both rights removed for the given color
     * (typically called when the king moves)
     */
    fun withoutRightsFor(color: Color): CastlingRights = when (color) {
        Color.WHITE -> copy(whiteKingSide = false, whiteQueenSide = false)
        Color.BLACK -> copy(blackKingSide = false, blackQueenSide = false)
    }

    /**
     * Returns castling rights with kingside right removed for the given color
     */
    fun withoutKingsideFor(color: Color): CastlingRights = when (color) {
        Color.WHITE -> copy(whiteKingSide = false)
        Color.BLACK -> copy(blackKingSide = false)
    }

    /**
     * Returns castling rights with queenside right removed for the given color
     */
    fun withoutQueensideFor(color: Color): CastlingRights = when (color) {
        Color.WHITE -> copy(whiteQueenSide = false)
        Color.BLACK -> copy(blackQueenSide = false)
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
