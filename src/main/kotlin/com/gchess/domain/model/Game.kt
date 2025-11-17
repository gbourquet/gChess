package com.gchess.domain.model

data class Game(
    val id: String,
    val board: ChessPosition = ChessPosition.initial(),
    val currentPlayer: Color = Color.WHITE,
    val status: GameStatus = GameStatus.IN_PROGRESS,
    val moveHistory: List<Move> = emptyList()
) {
    fun makeMove(move: Move): Game {
        return copy(
            board = board.movePiece(move.from, move.to),
            currentPlayer = currentPlayer.opposite(),
            moveHistory = moveHistory + move
        )
    }

    fun isPlayerTurn(color: Color): Boolean = currentPlayer == color

    fun isFinished(): Boolean = status in listOf(
        GameStatus.CHECKMATE,
        GameStatus.STALEMATE,
        GameStatus.DRAW
    )
}
