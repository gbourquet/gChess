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

import com.gchess.matchmaking.domain.model.BotMatchRequest
import com.gchess.matchmaking.domain.model.Match
import com.gchess.matchmaking.domain.port.BotSelector
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
 * Supports both human vs human and human vs bot matchmaking through the same interface.
 *
 * **Human vs Human Flow:**
 * 1. Validates the user exists (via ACL to User context)
 * 2. Adds the user to the FIFO queue
 * 3. Attempts to find a match with another waiting human
 * 4. If match found: creates a game, saves match, returns MATCHED
 * 5. If no match: returns WAITING with queue position
 *
 * **Human vs Bot Flow (when botRequest is provided):**
 * 1. Validates the user exists
 * 2. Selects a bot via ACL (specific or default)
 * 3. Creates Players with specified or random colors
 * 4. Creates a game immediately
 * 5. Saves match and returns MATCHED
 *
 * @property matchmakingQueue Queue for managing waiting users (human matchmaking)
 * @property gameCreator ACL port for creating games (to Chess context)
 * @property userExistenceChecker ACL for validating users exist (to User context)
 * @property botSelector ACL for selecting bots (to Bot context)
 * @property matchmakingNotifier Notifier for sending real-time updates via WebSocket
 */
class JoinMatchmakingUseCase(
    private val matchmakingQueue: MatchmakingQueue,
    private val gameCreator: GameCreator,
    private val userExistenceChecker: UserExistenceChecker,
    private val botSelector: BotSelector,
    private val matchmakingNotifier: MatchmakingNotifier
) {
    /**
     * Adds a user to the matchmaking queue.
     *
     * @param userId The ID of the user joining
     * @param botRequest Optional request to match with a bot instead of another human
     * @return Result.success(MatchmakingResult) indicating status (WAITING or MATCHED),
     *         or Result.failure if validation fails
     */
    suspend fun execute(userId: UserId, botRequest: BotMatchRequest? = null): Result<MatchmakingResult> {
        // 1. Validate user exists (ACL to User context)
        val userExists = try {
            userExistenceChecker.exists(userId)
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to validate user: ${e.message}", e))
        }

        if (!userExists) {
            return Result.failure(Exception("User does not exist"))
        }

        // 2. NOUVEAU: If bot match requested, handle immediately
        if (botRequest != null) {
            return handleBotMatch(userId, botRequest)
        }

        // 3. Validate user is not already in queue (human matchmaking only)
        if (matchmakingQueue.isPlayerInQueue(userId)) {
            return Result.failure(Exception("User is already in the matchmaking queue"))
        }

        // 4. Add user to queue
        matchmakingQueue.addPlayer(userId)

        // 5. Try to find a match
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

        // Create game via ACL
        val gameIdResult = gameCreator.createGame(whitePlayer, blackPlayer)
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

    /**
     * Handles bot match requests (immediate matching with a bot).
     *
     * @param humanUserId The user requesting to play against a bot
     * @param botRequest Configuration for the bot match (bot selection, color preference)
     * @return Result.success(MatchmakingResult.Matched) or Result.failure
     */
    private suspend fun handleBotMatch(
        humanUserId: UserId,
        botRequest: BotMatchRequest
    ): Result<MatchmakingResult> {
        // 1. Select bot via ACL (to Bot context)
        val botInfo = botSelector.selectBot(botRequest.botId)
            .getOrElse { return Result.failure(it) }

        // 2. Determine colors
        val humanColor = botRequest.playerColor
            ?: if (kotlin.random.Random.nextBoolean()) PlayerSide.WHITE else PlayerSide.BLACK
        val botColor = if (humanColor == PlayerSide.WHITE) PlayerSide.BLACK else PlayerSide.WHITE

        // 3. Create Players
        val humanPlayer = Player.create(humanUserId, humanColor)
        val botPlayer = Player.create(botInfo.systemUserId, botColor)

        // 4. Create game via ACL (to Chess context)
        val whitePlayer = if (humanColor == PlayerSide.WHITE) humanPlayer else botPlayer
        val blackPlayer = if (humanColor == PlayerSide.BLACK) humanPlayer else botPlayer

        val gameIdResult = gameCreator.createGame(whitePlayer, blackPlayer)
        if (gameIdResult.isFailure) {
            return Result.failure(gameIdResult.exceptionOrNull()!!)
        }

        val gameId = gameIdResult.getOrNull()!!

        // 5. Create match and notify human player (bot doesn't need notification)
        val match = Match.create(
            whitePlayer = whitePlayer,
            blackPlayer = blackPlayer,
            gameId = gameId
        )

        matchmakingNotifier.notifyMatchFound(match)

        return Result.success(MatchmakingResult.Matched(
            gameId = gameId,
            youPlayerId = humanPlayer.id,
            yourColor = humanColor
        ))
    }
}
