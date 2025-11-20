package com.gchess.matchmaking.application.usecase

import com.gchess.matchmaking.domain.port.MatchmakingQueue
import com.gchess.shared.domain.model.PlayerId

/**
 * Use case for leaving the matchmaking queue.
 *
 * This use case allows a player to exit the queue before being matched.
 * Once a player is matched, they cannot leave via this use case (the match already exists).
 *
 * @property matchmakingQueue Queue for managing waiting players
 */
class LeaveMatchmakingUseCase(
    private val matchmakingQueue: MatchmakingQueue
) {
    /**
     * Removes a player from the matchmaking queue.
     *
     * @param playerId The ID of the player leaving the queue
     * @return Result.success(true) if player was removed,
     *         Result.success(false) if player was not in queue
     */
    suspend fun execute(playerId: PlayerId): Result<Boolean> {
        val removed = matchmakingQueue.removePlayer(playerId)
        return Result.success(removed)
    }
}
