package com.gchess.application.usecase

import com.gchess.domain.model.Game
import com.gchess.domain.model.Move
import com.gchess.domain.port.GameRepository
import com.gchess.domain.port.MoveValidator

class MakeMoveUseCase(
    private val gameRepository: GameRepository,
    private val moveValidator: MoveValidator
) {
    suspend fun execute(gameId: String, move: Move): Result<Game> {
        val game = gameRepository.findById(gameId)
            ?: return Result.failure(Exception("Game not found"))

        if (game.isFinished()) {
            return Result.failure(Exception("Game is already finished"))
        }

        if (!moveValidator.isValidMove(game, move)) {
            return Result.failure(Exception("Invalid move"))
        }

        val updatedGame = game.makeMove(move)
        gameRepository.save(updatedGame)

        return Result.success(updatedGame)
    }
}
