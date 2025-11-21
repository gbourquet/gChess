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
package com.gchess.matchmaking.application.usecase

import com.gchess.matchmaking.domain.model.Match
import com.gchess.matchmaking.domain.port.GameCreator
import com.gchess.shared.domain.model.PlayerId
import kotlin.random.Random

/**
 * Use case for creating a chess game from a matched pair of players.
 *
 * This use case:
 * - Randomly assigns white/black colors to the two players (50/50 distribution)
 * - Calls the GameCreator (ACL to Chess context) to create the game
 * - Creates a Match entity with the game ID and player assignments
 *
 * @property gameCreator ACL port for creating games in Chess context
 * @property random Random number generator (injectable for testing)
 */
class CreateGameFromMatchUseCase(
    private val gameCreator: GameCreator,
    private val random: Random = Random.Default
) {
    /**
     * Creates a game for two matched players with random color assignment.
     *
     * @param player1Id First player's ID
     * @param player2Id Second player's ID
     * @return Result.success(Match) with game details, or Result.failure if game creation fails
     */
    suspend fun execute(player1Id: PlayerId, player2Id: PlayerId): Result<Match> {
        // Randomly assign colors (50/50)
        val (whitePlayerId, blackPlayerId) = if (random.nextBoolean()) {
            Pair(player1Id, player2Id)
        } else {
            Pair(player2Id, player1Id)
        }

        // Create game via ACL (Chess context)
        val gameResult = gameCreator.createGame(whitePlayerId, blackPlayerId)

        // Transform Result<GameId> to Result<Match>
        return gameResult.map { gameId ->
            Match.create(
                whitePlayerId = whitePlayerId,
                blackPlayerId = blackPlayerId,
                gameId = gameId
            )
        }
    }
}
