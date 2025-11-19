package com.gchess.chess.application.usecase

import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.port.GameRepository
import com.gchess.chess.domain.port.PlayerExistenceChecker
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId

class CreateGameUseCase(
    private val gameRepository: GameRepository,
    private val playerExistenceChecker: PlayerExistenceChecker
) {
    /**
     * Creates a new game with two players.
     *
     * Validates that both players exist in the system before creating the game.
     * Uses the Anti-Corruption Layer (PlayerExistenceChecker) to verify player existence
     * without directly depending on the User context.
     *
     * @param whitePlayerId The ID of the white player
     * @param blackPlayerId The ID of the black player
     * @return Result.success(Game) if game is created, Result.failure if validation fails
     */
    suspend fun execute(whitePlayerId: PlayerId, blackPlayerId: PlayerId): Result<Game> {
        // Validate that players are different
        if (whitePlayerId == blackPlayerId) {
            return Result.failure(Exception("White and black players must be different"))
        }

        // Validate that white player exists
        val whitePlayerExists = try {
            playerExistenceChecker.exists(whitePlayerId)
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to validate white player: ${e.message}", e))
        }

        if (!whitePlayerExists) {
            return Result.failure(Exception("White player ${whitePlayerId.value} does not exist"))
        }

        // Validate that black player exists
        val blackPlayerExists = try {
            playerExistenceChecker.exists(blackPlayerId)
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to validate black player: ${e.message}", e))
        }

        if (!blackPlayerExists) {
            return Result.failure(Exception("Black player ${blackPlayerId.value} does not exist"))
        }

        // Create and save the game
        val game = Game(
            id = GameId.generate(),
            whitePlayer = whitePlayerId,
            blackPlayer = blackPlayerId
        )

        return Result.success(gameRepository.save(game))
    }
}
