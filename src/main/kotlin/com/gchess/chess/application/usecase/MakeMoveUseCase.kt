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
import com.gchess.chess.domain.model.GameStatus
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
        val gameAfterMove = game.makeMove(move)

        // Evaluate game status after the move
        val updatedGame = updateGameStatus(gameAfterMove)
        gameRepository.save(updatedGame)

        return Result.success(updatedGame)
    }

    /**
     * Updates the game status based on the current position.
     *
     * Checks for game-ending conditions in order:
     * 1. Checkmate - opponent king in check with no legal moves
     * 2. Stalemate - opponent not in check but has no legal moves
     * 3. Fifty-move rule - 50 moves without capture or pawn move
     * 4. Insufficient material - impossible to checkmate
     * 5. Otherwise - game continues (IN_PROGRESS)
     *
     * Note: Threefold repetition is not yet implemented (requires position history tracking)
     */
    private fun updateGameStatus(game: Game): Game {
        val position = game.board

        val newStatus = when {
            chessRules.isCheckmate(position) -> GameStatus.CHECKMATE
            chessRules.isStalemate(position) -> GameStatus.STALEMATE
            chessRules.isFiftyMoveRule(position) -> GameStatus.DRAW
            chessRules.isInsufficientMaterial(position) -> GameStatus.DRAW
            else -> GameStatus.IN_PROGRESS
        }

        return game.copy(status = newStatus)
    }
}
