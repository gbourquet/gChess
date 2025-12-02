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
package com.gchess.bot.domain.port

import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.model.Move
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player

/**
 * Anti-Corruption Layer port for executing moves in the Chess context.
 *
 * This port is defined in the Bot context but implemented in the Bot infrastructure layer,
 * protecting the Bot domain from depending on Chess application details.
 *
 * The implementation will delegate to MakeMoveUseCase in the Chess context.
 */
interface MoveExecutor {
    /**
     * Executes a move for a bot player in a game.
     *
     * @param gameId The ID of the game
     * @param player The bot player making the move
     * @param move The move to execute
     * @return Result with updated Game if successful, or failure if move is illegal or game not found
     */
    suspend fun executeMove(gameId: GameId, player: Player, move: Move): Result<Game>
}
