package com.gchess.matchmaking.application.usecase

import com.gchess.shared.domain.model.GameId

/**
 * Result of a matchmaking status check.
 *
 * This sealed class represents the different states a player can be in:
 * - NotFound: Player is neither in queue nor matched
 * - Waiting: Player is in the queue waiting for an opponent
 * - Matched: Player has been matched and a game has been created
 */
sealed class MatchmakingResult {
    /**
     * Player is not in queue and has no active match.
     */
    data object NotFound : MatchmakingResult()

    /**
     * Player is waiting in the queue.
     *
     * @property queuePosition Position in the queue (1-indexed)
     */
    data class Waiting(val queuePosition: Int) : MatchmakingResult()

    /**
     * Player has been matched with an opponent.
     *
     * @property gameId ID of the created game
     * @property yourColor The color assigned to this player (WHITE or BLACK)
     */
    data class Matched(
        val gameId: GameId,
        val yourColor: PlayerSide
    ) : MatchmakingResult()

    /**
     * Represents the player's side in the game.
     */
    enum class PlayerSide {
        WHITE,
        BLACK
    }
}
