package com.gchess.matchmaking.domain.port

import com.gchess.matchmaking.domain.model.Match
import com.gchess.shared.domain.model.PlayerId

/**
 * Port (interface) for managing created matches.
 *
 * This repository stores matches after they are created, allowing players
 * to retrieve their match information (game ID and color assignment).
 *
 * Each match should be indexed by both player IDs to allow either player
 * to retrieve the match information.
 */
interface MatchRepository {
    /**
     * Saves a match to the repository.
     *
     * The match should be indexed by both whitePlayerId and blackPlayerId
     * so either player can retrieve it.
     *
     * @param match The match to save
     */
    suspend fun save(match: Match)

    /**
     * Finds a match for a specific player.
     *
     * @param playerId The unique identifier of the player
     * @return The Match if found, null otherwise
     */
    suspend fun findByPlayer(playerId: PlayerId): Match?

    /**
     * Deletes a match associated with a specific player.
     *
     * This should remove the match for both players (white and black).
     *
     * @param playerId The unique identifier of either player in the match
     */
    suspend fun delete(playerId: PlayerId)

    /**
     * Deletes all expired matches from the repository.
     *
     * This method should be called periodically to clean up old matches
     * that have exceeded their TTL (Time To Live).
     */
    suspend fun deleteExpiredMatches()
}
