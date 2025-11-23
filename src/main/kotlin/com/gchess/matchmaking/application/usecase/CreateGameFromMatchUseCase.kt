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

import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerSide
import com.gchess.matchmaking.domain.model.Match
import com.gchess.matchmaking.domain.port.GameCreator
import com.gchess.matchmaking.domain.port.UserExistenceChecker
import com.gchess.shared.domain.model.UserId
import kotlin.random.Random

/**
 * Use case for creating a chess game from a matched pair of users.
 *
 * This use case is responsible for:
 * 1. Validating that both users exist (via UserExistenceChecker ACL)
 * 2. Randomly assigning white/black colors to the two users (50/50 distribution)
 * 3. Creating Player objects with generated PlayerIds and assigned sides
 * 4. Calling the GameCreator (ACL to Chess context) to create the game with the Players
 * 5. Creating a Match entity with the game ID and user assignments
 *
 * This is a CRITICAL use case as it bridges the User and Chess contexts by creating
 * Player entities (Chess concept) from UserId (User concept).
 *
 * @property gameCreator ACL port for creating games in Chess context
 * @property userExistenceChecker ACL port for validating users in User context
 * @property random Random number generator (injectable for testing)
 */
class CreateGameFromMatchUseCase(
    private val gameCreator: GameCreator,
    private val userExistenceChecker: UserExistenceChecker,
    private val random: Random = Random.Default
) {
    /**
     * Creates a game for two matched users with random color assignment.
     *
     * @param user1Id First user's ID
     * @param user2Id Second user's ID
     * @return Result.success(Match) with game details, or Result.failure if validation or game creation fails
     */
    suspend fun execute(user1Id: UserId, user2Id: UserId): Result<Match> {
        // Step 1: Validate both users exist
        if (!userExistenceChecker.exists(user1Id)) {
            return Result.failure(Exception("User 1 does not exist: ${user1Id.value}"))
        }
        if (!userExistenceChecker.exists(user2Id)) {
            return Result.failure(Exception("User 2 does not exist: ${user2Id.value}"))
        }

        // Step 2: Randomly assign colors (50/50 distribution)
        val (whiteUserId, blackUserId) = if (random.nextBoolean()) {
            Pair(user1Id, user2Id)
        } else {
            Pair(user2Id, user1Id)
        }

        // Step 3: Create Player objects with generated PlayerIds and assigned sides
        // This is where we convert from User concept (UserId) to Chess concept (Player)
        val whitePlayer = Player.create(whiteUserId, PlayerSide.WHITE)
        val blackPlayer = Player.create(blackUserId, PlayerSide.BLACK)

        // Step 4: Create game via ACL (Chess context) with Player objects
        val gameResult = gameCreator.createGame(whitePlayer, blackPlayer)

        // Step 5: Transform Result<GameId> to Result<Match>
        return gameResult.map { gameId ->
            Match.create(
                whiteUserId = whiteUserId,
                blackUserId = blackUserId,
                gameId = gameId
            )
        }
    }
}

