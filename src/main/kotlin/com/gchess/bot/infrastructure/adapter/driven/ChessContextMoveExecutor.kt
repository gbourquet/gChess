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
package com.gchess.bot.infrastructure.adapter.driven

import com.gchess.bot.domain.port.MoveExecutor
import com.gchess.chess.application.usecase.MakeMoveUseCase
import com.gchess.chess.domain.model.Game
import com.gchess.chess.domain.model.Move
import com.gchess.shared.domain.model.GameId
import com.gchess.shared.domain.model.Player

/**
 * Anti-Corruption Layer adapter from Bot context to Chess context.
 *
 * Delegates move execution to MakeMoveUseCase in the Chess context,
 * protecting the Bot domain from depending on Chess application details.
 */
class ChessContextMoveExecutor(
    private val makeMoveUseCase: MakeMoveUseCase
) : MoveExecutor {

    override suspend fun executeMove(gameId: GameId, player: Player, move: Move): Result<Game> {
        return makeMoveUseCase.execute(gameId, player, move)
    }
}
