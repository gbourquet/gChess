package com.gchess.chess.application.usecase

import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.model.Move
import com.gchess.chess.domain.port.GameRepository
import com.gchess.chess.domain.port.PlayerExistenceChecker
import com.gchess.chess.domain.service.ChessRules
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId

class MakeMoveUseCase(
    private val gameRepository: GameRepository,
    private val chessRules: ChessRules,
    private val playerExistenceChecker: PlayerExistenceChecker
) {
    /**
     * Executes a move in a game.
     *
     * Validates:
     * - The player exists (via ACL)
     * - It's the player's turn
     * - The game is not finished
     * - The move is legal
     *
     * @param gameId The ID of the game
     * @param playerId The ID of the player making the move
     * @param move The move to execute
     * @return Result.success(Game) if move is valid, Result.failure otherwise
     */
    suspend fun execute(gameId: GameId, playerId: PlayerId, move: Move): Result<Game> {
        // Verify player exists
        val playerExists = try {
            playerExistenceChecker.exists(playerId)
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to validate player: ${e.message}", e))
        }

        if (!playerExists) {
            return Result.failure(Exception("Player ${playerId.value} does not exist"))
        }

        // Find the game
        val game = gameRepository.findById(gameId)
            ?: return Result.failure(Exception("Game not found"))

        // Verify it's the player's turn
        if (!game.isPlayerTurn(playerId)) {
            return Result.failure(Exception("It's not player ${playerId.value}'s turn"))
        }

        // Check if game is finished
        if (game.isFinished()) {
            return Result.failure(Exception("Game is already finished"))
        }

        // Verify move is legal
        if (!chessRules.isMoveLegal(game.board, move)) {
            return Result.failure(Exception("Invalid move"))
        }

        // Execute the move
        val updatedGame = game.makeMove(move)
        gameRepository.save(updatedGame)

        return Result.success(updatedGame)
    }
}
