package com.gchess.domain.model

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
