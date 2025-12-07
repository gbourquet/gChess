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
import com.gchess.chess.domain.port.GameEventNotifier
import com.gchess.chess.domain.port.GameRepository
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player

/**
 * Use case for resigning from a chess game.
 *
 * This use case allows a player to resign from a game, ending it immediately.
 * The player who resigns loses the game.
 *
 * @property gameRepository Repository for persisting games
 * @property gameEventNotifier Notifier for sending real-time updates via WebSocket
 */
class ResignGameUseCase(
    private val gameRepository: GameRepository,
    private val gameEventNotifier: GameEventNotifier
) {
    /**
     * Resigns from a game.
     *
     * Validates:
     * - The game exists
     * - The player is a participant in the game
     * - The game is not already finished
     *
     * @param gameId The ID of the game
     * @param player The player resigning
     * @return Result.success(Game) if resignation is successful, Result.failure otherwise
     */
    suspend fun execute(gameId: GameId, player: Player): Result<Game> {
        val game = gameRepository.findById(gameId)
            ?: return Result.failure(Exception("Game not found"))

        if (!isPlayerInGame(game, player)) {
            return Result.failure(Exception("You are not a participant in this game"))
        }

        if (game.isFinished()) {
            return Result.failure(Exception("Game is already finished"))
        }

        val resignedGame = game.copy(status = GameStatus.RESIGNED)
        gameRepository.save(resignedGame)

        gameEventNotifier.notifyGameResigned(resignedGame, player)

        return Result.success(resignedGame)
    }

    private fun isPlayerInGame(game: Game, player: Player): Boolean {
        return game.whitePlayer == player || game.blackPlayer == player
    }
}