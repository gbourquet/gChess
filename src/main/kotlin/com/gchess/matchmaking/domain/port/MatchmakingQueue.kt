package com.gchess.matchmaking.domain.port

import com.gchess.matchmaking.domain.model.QueueEntry
import com.gchess.shared.domain.model.PlayerId

/**
 * Port (interface) for managing the matchmaking queue.
 *
 * This interface defines operations for adding/removing players from the queue
 * and finding matches between waiting players.
 *
 * Implementations should ensure thread-safety and FIFO (First-In-First-Out) ordering.
 */
interface MatchmakingQueue {
    /**
     * Adds a player to the matchmaking queue.
     *
     * @param playerId The unique identifier of the player to add
     * @return The created QueueEntry
     * @throws IllegalStateException if the player is already in the queue
     */
    suspend fun addPlayer(playerId: PlayerId): QueueEntry

    /**
     * Removes a player from the matchmaking queue.
     *
     * @param playerId The unique identifier of the player to remove
     * @return true if the player was removed, false if the player was not in the queue
     */
    suspend fun removePlayer(playerId: PlayerId): Boolean

    /**
     * Attempts to find a match between two players in the queue.
     *
     * This method should:
     * - Return null if there are fewer than 2 players in the queue
     * - Remove both matched players from the queue atomically
     * - Follow FIFO ordering (match the two oldest entries)
     *
     * @return A Pair of the two matched QueueEntries, or null if no match is possible
     */
    suspend fun findMatch(): Pair<QueueEntry, QueueEntry>?

    /**
     * Checks if a specific player is currently in the queue.
     *
     * @param playerId The unique identifier of the player to check
     * @return true if the player is in the queue, false otherwise
     */
    suspend fun isPlayerInQueue(playerId: PlayerId): Boolean

    /**
     * Gets the current number of players in the queue.
     *
     * @return The queue size
     */
    suspend fun getQueueSize(): Int
}
