package com.gchess.domain.model

data class Piece(
    val type: PieceType,
    val color: Color,
    val hasMoved: Boolean = false
) {
    fun withMoved(): Piece = copy(hasMoved = true)

    /**
     * Converts this piece to its FEN character representation
     * Uppercase for white pieces, lowercase for black pieces
     */
    fun toFenChar(): Char {
        val baseChar = when (type) {
            PieceType.PAWN -> 'P'
            PieceType.KNIGHT -> 'N'
            PieceType.BISHOP -> 'B'
            PieceType.ROOK -> 'R'
            PieceType.QUEEN -> 'Q'
            PieceType.KING -> 'K'
        }
        return if (color == Color.WHITE) baseChar else baseChar.lowercaseChar()
    }
}
