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
package com.gchess.matchmaking.infrastructure.adapter.driven

import com.gchess.chess.application.usecase.CreateGameUseCase
import com.gchess.shared.domain.model.Player
import com.gchess.matchmaking.domain.port.GameCreator
import com.gchess.shared.domain.model.GameId

/**
 * Anti-Corruption Layer adapter that allows the Matchmaking context
 * to create games by communicating with the Chess context.
 *
 * This adapter:
 * - Implements the GameCreator port (defined in Matchmaking domain)
 * - Receives Player objects (created by Matchmaking) with assigned colors
 * - Calls CreateGameUseCase from the Chess context with these Players
 * - Transforms Result<Game> to Result<GameId>
 * - Maintains bounded context isolation
 *
 * **IMPORTANT**: The Matchmaking context creates the Player objects (including
 * PlayerId generation and color assignment) before calling this adapter.
 */
class ChessContextGameCreator(
    private val createGameUseCase: CreateGameUseCase
) : GameCreator {

    override suspend fun createGame(whitePlayer: Player, blackPlayer: Player): Result<GameId> {
        // Call Chess context use case with Player objects
        // The Matchmaking context has already created these Players with:
        // - Generated PlayerIds
        // - Assigned UserId (from matched users)
        // - Assigned PlayerSide (WHITE/BLACK with random 50/50 distribution)
        val gameResult = createGameUseCase.execute(whitePlayer, blackPlayer)

        // Transform Result<Game> to Result<GameId>
        return gameResult.map { game ->
            game.id // Extract GameId from Game entity
        }
    }
}
