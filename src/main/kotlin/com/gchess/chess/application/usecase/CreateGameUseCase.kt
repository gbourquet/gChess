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

import com.gchess.chess.domain.model.ChessPosition
import com.gchess.chess.domain.model.Game
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerSide
import com.gchess.chess.domain.model.toChessPosition
import com.gchess.chess.domain.port.GameRepository
import com.gchess.shared.domain.model.GameId

/**
 * Use case for creating a new chess game.
 *
 * This use case takes two Players (already created and validated by the caller,
 * typically the Matchmaking context) and creates a Game.
 *
 * Responsibilities:
 * - Validates that white player has WHITE side and black player has BLACK side
 * - Creates the game with the given players
 * - Saves the game to the repository
 *
 * Note: User existence validation and Player creation are done BEFORE calling this use case.
 * This keeps the Chess context isolated and focused on chess game logic.
 */
open class CreateGameUseCase(
    private val gameRepository: GameRepository
) {
    /**
     * Creates a new game with two players.
     *
     * @param whitePlayer The white player (must have PlayerSide.WHITE)
     * @param blackPlayer The black player (must have PlayerSide.BLACK)
     * @param initialPosition Optional FEN string to set a custom starting position (defaults to standard chess position)
     * @return Result.success(Game) if game is created, Result.failure if validation fails
     */
    open suspend fun execute(whitePlayer: Player, blackPlayer: Player, initialPosition: String? = null): Result<Game> {
        // Validate that white player has WHITE side
        if (whitePlayer.side != PlayerSide.WHITE) {
            return Result.failure(Exception("White player must have WHITE side, got ${whitePlayer.side}"))
        }

        // Validate that black player has BLACK side
        if (blackPlayer.side != PlayerSide.BLACK) {
            return Result.failure(Exception("Black player must have BLACK side, got ${blackPlayer.side}"))
        }

        // Validate that players are different users
        if (whitePlayer.userId == blackPlayer.userId) {
            return Result.failure(Exception("White and black players must be different users"))
        }

        // Parse initial position if provided
        val board = if (initialPosition != null) {
            try {
                initialPosition.toChessPosition()
            } catch (e: Exception) {
                return Result.failure(Exception("Invalid FEN notation: ${e.message}", e))
            }
        } else {
            ChessPosition.initial()
        }

        // Create and save the game
        val game = Game(
            id = GameId.generate(),
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            board = board,
            currentSide = board.sideToMove
        )

        return Result.success(gameRepository.save(game))
    }
}
