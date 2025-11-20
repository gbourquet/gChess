package com.gchess.matchmaking.application.usecase

import com.gchess.chess.domain.port.PlayerExistenceChecker
import com.gchess.matchmaking.domain.port.MatchRepository
import com.gchess.matchmaking.domain.port.MatchmakingQueue
import com.gchess.shared.domain.model.PlayerId

/**
 * Use case for joining the matchmaking queue.
 *
 * This is the main matchmaking use case that:
 * 1. Validates the player exists (via ACL to User context)
 * 2. Validates the player is not already in queue
 * 3. Validates the player doesn't have an active match
 * 4. Adds the player to the queue (with lock for thread-safety)
 * 5. Attempts to find a match immediately
 * 6. If match found:
 *    - Creates a game automatically (via CreateGameFromMatchUseCase)
 *    - Saves the match to the repository
 *    - Returns MATCHED status
 * 7. If no match found:
 *    - Returns WAITING status
 *
 * @property matchmakingQueue Queue for managing waiting players
 * @property matchRepository Repository for storing matches
 * @property playerExistenceChecker ACL for validating players exist
 * @property createGameFromMatchUseCase Use case for creating games from matches
 */
class JoinMatchmakingUseCase(
    private val matchmakingQueue: MatchmakingQueue,
    private val matchRepository: MatchRepository,
    private val playerExistenceChecker: PlayerExistenceChecker,
    private val createGameFromMatchUseCase: CreateGameFromMatchUseCase
) {
    /**
     * Adds a player to the matchmaking queue.
     *
     * @param playerId The ID of the player joining
     * @return Result.success(MatchmakingResult) indicating status (WAITING or MATCHED),
     *         or Result.failure if validation fails
     */
    suspend fun execute(playerId: PlayerId): Result<MatchmakingResult> {
        // 1. Validate player exists (ACL to User context)
        val playerExists = try {
            playerExistenceChecker.exists(playerId)
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to validate player: ${e.message}", e))
        }

        if (!playerExists) {
            return Result.failure(Exception("Player does not exist"))
        }

        // 2. Validate player is not already in queue
        if (matchmakingQueue.isPlayerInQueue(playerId)) {
            return Result.failure(Exception("Player is already in the matchmaking queue"))
        }

        // 3. Validate player doesn't have an active match
        val existingMatch = matchRepository.findByPlayer(playerId)
        if (existingMatch != null && !existingMatch.isExpired()) {
            return Result.failure(Exception("Player already has an active match"))
        }

        // 4. Add player to queue
        // Note: The queue implementation uses a lock internally for thread-safety
        matchmakingQueue.addPlayer(playerId)

        // 5. Try to find a match
        val matchPair = matchmakingQueue.findMatch()

        if (matchPair == null) {
            // 6a. No match found - player is waiting
            return Result.success(MatchmakingResult.Waiting(
                queuePosition = matchmakingQueue.getQueueSize()
            ))
        }

        // 6b. Match found - create game automatically
        val (player1Entry, player2Entry) = matchPair
        val player1Id = player1Entry.playerId
        val player2Id = player2Entry.playerId

        // Create game via CreateGameFromMatchUseCase
        val gameResult = createGameFromMatchUseCase.execute(player1Id, player2Id)

        if (gameResult.isFailure) {
            // Game creation failed - propagate error
            // Note: Players were already removed from queue by findMatch()
            // In a production system, we might want to add them back
            return Result.failure(gameResult.exceptionOrNull()!!)
        }

        val match = gameResult.getOrNull()!!

        // Save match to repository (indexed by both players)
        matchRepository.save(match)

        // Determine which player is calling this method and return their color
        val yourColor = when (playerId) {
            match.whitePlayerId -> MatchmakingResult.PlayerSide.WHITE
            match.blackPlayerId -> MatchmakingResult.PlayerSide.BLACK
            else -> {
                // This should never happen unless there's a logic error
                // The calling player should be one of the matched players
                error("Caller $playerId not found in match")
            }
        }

        return Result.success(MatchmakingResult.Matched(
            gameId = match.gameId,
            yourColor = yourColor
        ))
    }
}
