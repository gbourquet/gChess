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

import com.gchess.shared.domain.model.PlayerSide
import com.gchess.matchmaking.domain.port.MatchRepository
import com.gchess.matchmaking.domain.port.MatchmakingQueue
import com.gchess.matchmaking.domain.port.UserExistenceChecker
import com.gchess.shared.domain.model.UserId

/**
 * Use case for joining the matchmaking queue.
 *
 * This is the main matchmaking use case that:
 * 1. Validates the user exists (via ACL to User context)
 * 2. Validates the user is not already in queue
 * 3. Validates the user doesn't have an active match
 * 4. Adds the user to the queue (with lock for thread-safety)
 * 5. Attempts to find a match immediately
 * 6. If match found:
 *    - Creates a game automatically (via CreateGameFromMatchUseCase)
 *    - Saves the match to the repository
 *    - Returns MATCHED status
 * 7. If no match found:
 *    - Returns WAITING status
 *
 * @property matchmakingQueue Queue for managing waiting users
 * @property matchRepository Repository for storing matches
 * @property userExistenceChecker ACL for validating users exist
 * @property createGameFromMatchUseCase Use case for creating games from matches
 */
class JoinMatchmakingUseCase(
    private val matchmakingQueue: MatchmakingQueue,
    private val matchRepository: MatchRepository,
    private val userExistenceChecker: UserExistenceChecker,
    private val createGameFromMatchUseCase: CreateGameFromMatchUseCase
) {
    /**
     * Adds a user to the matchmaking queue.
     *
     * @param userId The ID of the user joining
     * @return Result.success(MatchmakingResult) indicating status (WAITING or MATCHED),
     *         or Result.failure if validation fails
     */
    suspend fun execute(userId: UserId): Result<MatchmakingResult> {
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

        // 3. Validate user doesn't have an active match
        val existingMatch = matchRepository.findByPlayer(userId)
        if (existingMatch != null && !existingMatch.isExpired()) {
            return Result.failure(Exception("User already has an active match"))
        }

        // 4. Add user to queue
        // Note: The queue implementation uses a lock internally for thread-safety
        matchmakingQueue.addPlayer(userId)

        // 5. Try to find a match
        val matchPair = matchmakingQueue.findMatch() ?: // 6a. No match found - user is waiting
        return Result.success(
            MatchmakingResult.Waiting(
                queuePosition = matchmakingQueue.getQueueSize()
            )
        )

        // 6b. Match found - create game automatically
        val (user1Entry, user2Entry) = matchPair
        val user1Id = user1Entry.userId
        val user2Id = user2Entry.userId

        // Create game via CreateGameFromMatchUseCase
        val gameResult = createGameFromMatchUseCase.execute(user1Id, user2Id)

        if (gameResult.isFailure) {
            // Game creation failed - propagate error
            // Note: Users were already removed from queue by findMatch()
            // In a production system, we might want to add them back
            return Result.failure(gameResult.exceptionOrNull()!!)
        }

        val match = gameResult.getOrNull()!!

        // Save match to repository (indexed by both users)
        matchRepository.save(match)

        // Determine which user is calling this method and return their color
        val yourColor = when (userId) {
            match.whiteUserId -> PlayerSide.WHITE
            match.blackUserId -> PlayerSide.BLACK
            else -> {
                // This should never happen unless there's a logic error
                // The calling user should be one of the matched users
                error("Caller $userId not found in match")
            }
        }

        return Result.success(MatchmakingResult.Matched(
            gameId = match.gameId,
            yourColor = yourColor
        ))
    }
}
