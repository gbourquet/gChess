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
package com.gchess.chess.application.usecase

import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.port.GameRepository
import com.gchess.chess.domain.port.PlayerExistenceChecker
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.PlayerId

open class CreateGameUseCase(
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
    open suspend fun execute(whitePlayerId: PlayerId, blackPlayerId: PlayerId): Result<Game> {
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
