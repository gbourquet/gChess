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
package com.gchess.bot.application.usecase

import com.gchess.bot.domain.service.BotService
import com.gchess.bot.domain.port.BotRepository
import com.gchess.bot.domain.port.MoveExecutor
import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.port.GameRepository
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player

/**
 * Use case for executing a bot's move in a game.
 *
 * This is the core bot gameplay logic:
 * 1. Retrieve the game state
 * 2. Retrieve the bot configuration (for difficulty)
 * 3. Calculate the best move using the bot engine
 * 4. Execute the move via ACL to Chess context
 *
 * WebSocket notifications are handled automatically by MakeMoveUseCase.
 */
class ExecuteBotMoveUseCase(
    private val botService: BotService,
    private val gameRepository: GameRepository,
    private val moveExecutor: MoveExecutor,
    private val botRepository: BotRepository
) {
    /**
     * Calculates and executes the bot's move.
     *
     * @param gameId The ID of the game
     * @param botPlayer The bot player making the move
     * @return Result with updated Game if successful, or failure if calculation/execution fails
     */
    suspend fun execute(gameId: GameId, botPlayer: Player): Result<Game> {
        // 1. Retrieve the game
        val game = gameRepository.findById(gameId)
            ?: return Result.failure(Exception("Game not found: $gameId"))

        // 2. Retrieve the bot for difficulty information
        val bot = botRepository.findBySystemUserId(botPlayer.userId)
            ?: return Result.failure(Exception("Bot not found for user: ${botPlayer.userId}"))

        // 3. Calculate the best move using the bot engine
        val evaluation = botService.calculateBestMove(
            position = game.board,
            difficulty = bot.difficulty
        ).getOrElse { return Result.failure(it) }

        // 4. Execute the move via ACL (triggers WebSocket notifications automatically)
        return moveExecutor.executeMove(gameId, botPlayer, evaluation.move)
    }
}
