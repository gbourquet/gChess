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
import com.gchess.matchmaking.domain.port.MatchmakingNotifier
import com.gchess.matchmaking.domain.port.MatchmakingQueue
import com.gchess.matchmaking.domain.port.UserExistenceChecker
import com.gchess.shared.domain.model.Player
import com.gchess.shared.domain.model.PlayerSide
import com.gchess.shared.domain.model.UserId

/**
 * Use case for joining the matchmaking queue.
 *
 * **Human vs Human Flow:**
 * 1. Validates the user exists (via ACL to User context)
 * 2. Adds the user to the FIFO queue
 * 3. Attempts to find a match with another waiting human
 * 4. If match found: creates a game, saves match, returns MATCHED
 * 5. If no match: returns WAITING with queue position
 *
 * @property matchmakingQueue Queue for managing waiting users
 * @property gameCreator ACL port for creating games (to Chess context)
 * @property userExistenceChecker ACL for validating users exist (to User context)
 * @property matchmakingNotifier Notifier for sending real-time updates via WebSocket
 */
class JoinMatchmakingUseCase(
    private val matchmakingQueue: MatchmakingQueue,
    private val gameCreator: GameCreator,
    private val userExistenceChecker: UserExistenceChecker,
    private val matchmakingNotifier: MatchmakingNotifier
) {
    /**
     * Adds a user to the matchmaking queue.
     *
     * @param userId The ID of the user joining
     * @return Result.success(MatchmakingResult) indicating status (WAITING or MATCHED),
     *         or Result.failure if validation fails
     */
    suspend fun execute(
        userId: UserId,
        totalTimeSeconds: Int = 0,
        incrementSeconds: Int = 0
    ): Result<MatchmakingResult> {
        // 1. Validate user exists (ACL to User context)
        val userExists = try {
            userExistenceChecker.exists(userId)
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to validate user: ${e.message}", e))
        }

        if (!userExists) {
            return Result.failure(Exception("User does not exist"))
        }

        // 2. Validate user is not already in queue
        if (matchmakingQueue.isPlayerInQueue(userId)) {
            return Result.failure(Exception("User is already in the matchmaking queue"))
        }

        // 3. Add user to queue with time control preferences
        matchmakingQueue.addPlayer(userId, totalTimeSeconds, incrementSeconds)

        // 4. Try to find a match (only compatible time controls are paired)
        val matchPair = matchmakingQueue.findMatch()

        if (matchPair == null) {
            // No match found - user is waiting
            val queuePosition = matchmakingQueue.getQueueSize()
            matchmakingNotifier.notifyQueuePosition(userId, queuePosition)

            return Result.success(
                MatchmakingResult.Waiting(queuePosition = queuePosition)
            )
        }

        // Match found - create game with another human
        val (user1Entry, user2Entry) = matchPair
        val user1Id = user1Entry.userId
        val user2Id = user2Entry.userId

        // Randomly assign colors
        val (whiteUserId, blackUserId) = if (kotlin.random.Random.nextBoolean()) {
            user1Id to user2Id
        } else {
            user2Id to user1Id
        }

        val whitePlayer = Player.create(whiteUserId, PlayerSide.WHITE)
        val blackPlayer = Player.create(blackUserId, PlayerSide.BLACK)

        // Create game via ACL, passing time control as primitives
        val gameIdResult = gameCreator.createGame(
            whitePlayer,
            blackPlayer,
            user1Entry.totalTimeSeconds,
            user1Entry.incrementSeconds
        )
        if (gameIdResult.isFailure) {
            return Result.failure(gameIdResult.exceptionOrNull()!!)
        }

        val gameId = gameIdResult.getOrNull()!!

        // Create match and send notifications
        val match = Match.create(
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            gameId = gameId
        )

        matchmakingNotifier.notifyMatchFound(match)

        // Return result for the calling user
        val yourColor = if (userId == whiteUserId) PlayerSide.WHITE else PlayerSide.BLACK
        val yourPlayer = if (userId == whiteUserId) whitePlayer else blackPlayer

        return Result.success(MatchmakingResult.Matched(
            gameId = gameId,
            youPlayerId = yourPlayer.id,
            yourColor = yourColor
        ))
    }
}
