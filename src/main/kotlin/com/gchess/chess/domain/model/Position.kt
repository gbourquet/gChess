package com.gchess.chess.domain.model

data class Position(val file: Int, val rank: Int) {
    init {
        require(file in 0..7) { "File must be between 0 and 7" }
        require(rank in 0..7) { "Rank must be between 0 and 7" }
    }

    companion object {
        fun fromAlgebraic(notation: String): Position {
            require(notation.length == 2) { "Invalid algebraic notation" }
            val file = notation[0].lowercaseChar() - 'a'
            val rank = notation[1].digitToInt() - 1
            return Position(file, rank)
        }
    }

    fun toAlgebraic(): String {
        val fileChar = ('a' + file)
        val rankChar = '1' + rank
        return "$fileChar$rankChar"
    }

    fun isValid(): Boolean = file in 0..7 && rank in 0..7
}
